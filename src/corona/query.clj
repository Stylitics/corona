(ns corona.query
  (:require [clojure.string :as string]
            [clj-http.client :as http]
            [clojure.data.json :as json]))


;;; Params

(defn format-param
  [p]
  (cond
    (and (sequential? p) (number? (last p))) (string/join "^" p)
    (keyword? p) (name p)
    :else (str p)))

(defn format-values
  [v]
  (mapv format-param (if (coll? v) v [v])))

(defn format-params
  [m]
  (reduce-kv
   (fn [m k v]
     (let [k* (format-param k)]
       (assoc m k* (string/join (if (#{"sort" "fl"} k*) "," " ")
                                (format-values v)))))
   {}
   m))


;;; Settings

(def default-term-vectors-settings
  {:tv true
   :tv.df true
   :tv.tf true
   ;;:tv.tf_idf true ;(* (Math/pow tf 0.5) (/ 1 df))
   :start 0
   :rows 1
   ;;:indent true
   })

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
  "NOTE: :fl is not considered to get back all keys of matching doc."
  [settings]
  (merge (select-keys settings mlt-keys)
         {:q (:mlt.q settings)
          :mlt.interestingTerms "details"
          :fq (:fq settings)}))


;;; Terms

(defn mlt-resp->terms
  [mlt-resp]
  (->> mlt-resp
       :interestingTerms
       (partition 2)
       (mapv (fn [[field+term score]]
               (let [[field term] (string/split field+term #"\:")]
                 [field term score])))))

(defn vectorize-qf-element
  [qf-element]
  (if (string? qf-element)
    (-> qf-element
        (string/split #"\^")
        ((fn [[qf-field qf-boost-str]]
           [qf-field (Float/parseFloat qf-boost-str)])))
    qf-element))

(defn mlt-resp->raw-terms
  [mlt-resp mlt-qf-raw]
  (let [matched (->> mlt-resp :match :docs first)]
    (reduce (fn [mlt-qf-raw match-el]
              [mlt-qf-raw match-el])
            mlt-qf-raw
            matched)))

(defn boost-terms
  [mlt-terms mlt-qf & [mlt-boost-factor]]
  (reduce
   (fn [sim-terms mlt-qf-element]
     (let [[mlt-qf-field mlt-qf-boost] (vectorize-qf-element mlt-qf-element)]
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
   mlt-qf ;;FIXME: when not there, qf boost one need also mlt.fl ?
   ))

(defn term-vectors-resp->interesting-terms-per-field
  "Digests the response from tvrh handler call targetting matching doc id."
  [tv-resp & [qf top-k]]
  (let [[id [_ _ & rst]] (:termVectors tv-resp)
        qf-map (into {} qf)]
    (for [[field tokens] (partition 2 rst)]
      [field (take
              (or top-k 8)
              (sort-by second >
                       (for [[token stats] (partition 2 tokens)
                             :let [[_ tf _ df] stats
                                   tf-idf (* (Math/pow tf 0.5) (/ 1 df))]]
                         [token (+ (+ 1 (* 100 tf-idf))
                                   (or (get qf-map field) 0))])))])))

(defn terms-per-field->q
  [terms-map]
  (->>  terms-map
        (mapcat (fn [[field terms]]
               (map (fn [[term score]] (str field ":" term "^" (or score 1)))
                       terms)))
        (string/join " ")))

(defn tv-terms->q
  [tv-q tv-terms & [q]]
  (let [tv-terms-str (terms-per-field->q tv-terms)]
    (format "-%s %s %s" tv-q tv-terms-str q)))

(defn terms->q
  ""
  [terms]
  (->> terms
       (map (fn [[field term score]]
              (str field ":" term "^" (or score 1))))
       (string/join " " )))

(defn mlt-terms->q
  [mlt-q mlt-terms & [q]]
  (let [mlt-terms-str (terms->q mlt-terms)]
    (format "-%s %s %s" mlt-q mlt-terms-str q)))



(comment
  ;; TODO: integrate unit tests

  (= {"terms" "true", "terms.prefix" "p", "terms.fl" "color color_tone"}
     (format-params
      {:terms true
       :terms.prefix "p"
       :terms.fl ["color" "color_tone"]}))

  (= (boost-terms [["retailer_category" "new" 1.0]
                   ["retailer_style" "dresses" 1.0]
                   ["name" "test" 1.0]]
                  ["name^3"])
     [["retailer_category" "new" 1.0]
      ["retailer_style" "dresses" 1.0]
      ["name" "test" 3.0]])

  (= "name:neck:2.0^1 name:stretch:1.0^1 style:even:3.0^1"
     (terms->q [["name:neck" 2.0]
                ["name:stretch" 1.0]
                ["style:even" 3.0]]))

  (= "name:neck^2.0 name:stretch^1.0 style:even^3.0"
     (terms-per-field->q
      [["name" [["neck" 2.0]
                ["stretch" 1.0]]]
       ["style" [["even" 3.0]]]]))
  )


