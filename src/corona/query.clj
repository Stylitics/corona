(ns corona.query
  (:require [clojure.string :as string])
  (:import [org.apache.solr.common.params MultiMapSolrParams CommonParams]
           [org.apache.solr.client.solrj.request QueryRequest]
           [org.apache.solr.client.solrj SolrRequest$METHOD]
           (java.util HashMap)))

(def method-map
  {:get  SolrRequest$METHOD/GET
   :post SolrRequest$METHOD/POST})

(defn- format-param
  [p]
  (cond
    (and (sequential? p) (number? (last p))) (string/join "^" p)
    (keyword? p) (name p)
    :else (str p)))

(defn- format-values
  [v]
  (into-array (mapv format-param (if (coll? v) v [v]))))

(defn create-solr-params ^MultiMapSolrParams
  [m]
  (MultiMapSolrParams.
    (reduce-kv (fn [^HashMap hm k v]
                 (doto hm
                   (.put (format-param k) (format-values v))))
               (HashMap.) m)))

(defn create-mlt-solr-params
  [m]
  (create-solr-params (update m CommonParams/QT #(if % % "/mlt"))))

;;; MLT Edismax

(defn mlt-resp->terms
  [mlt-resp]
  (->> mlt-resp
       :interestingTerms
       (mapv (fn [[field+term score]]
               (let [[field term] (string/split field+term #"\:")]
                 [field term score])))))

(defn boost-terms
  [mlt-terms mlt-qf & [mlt-boost-factor]]
  (reduce
   (fn [sim-terms mlt-qf-element]
     (let [[mlt-qf-field mlt-qf-boost] (cond-> mlt-qf-element
                                 (string? mlt-qf-element)
                                 (string/split #"\^")
                                 (string? mlt-qf-element)
                                 ((fn [[mlt-qf-field mlt-qf-boost-str]]
                                    [mlt-qf-field (Float/parseFloat mlt-qf-boost-str)])))]
       (reduce
        (fn [acc [field term score :as v]]
          (conj
           acc
           [field term (cond-> score
                         (= mlt-qf-field field) (* mlt-qf-boost
                                                   (or mlt-boost-factor 1)))]))
        []
        sim-terms)))
   mlt-terms
   mlt-qf))

(comment
  (= (boost-terms [["retailer_category" "new" 1.0]
                   ["retailer_style" "dresses" 1.0]
                   ["name" "test" 1.0]]
                  ["name^3"])
     [["retailer_category" "new" 1.0]
      ["retailer_style" "dresses" 1.0]
      ["name" "test" 3.0]])
  )


(defn terms->q
  [terms]
  (->> terms
       (map (fn [[field term score]]
              (str field ":" term "^" score)))
       (string/join " " )))


(defn mlt-terms->q
  [mlt-q mlt-terms & [q]]
  (let [mlt-terms-str (terms->q mlt-terms)]
    (format "-(%s) %s %s" mlt-q mlt-terms-str q)))

(def mlt-keys [:mlt.fl
               :mlt.mintf
               :mlt.mindf
               :mlt.maxdf
               :mlt.maxdfpct
               :mlt.minwl
               :mlt.maxwl
               :mlt.maxqt
               :mlt.maxntp
               :mlt.boost
               :mlt.qf
               :mlt.match.include
               :mlt.match.offset
               :mlt.interestingTerms])

(defn build-internal-mlt-settings
  [settings]
  (merge (select-keys settings mlt-keys)
         {:q (:mlt.q settings)
          :mlt.interestingTerms "details"}))

