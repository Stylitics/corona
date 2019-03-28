(ns corona.query
  (:import [org.apache.solr.common.params MultiMapSolrParams CommonParams]
           [org.apache.solr.client.solrj.request QueryRequest]
           [org.apache.solr.client.solrj SolrRequest$METHOD]
           (java.util HashMap)))

(def method-map
  {:get  SolrRequest$METHOD/GET
   :post SolrRequest$METHOD/POST})

(defn- format-param
  [p]
  (if (keyword? p) (name p) (str p)))

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
