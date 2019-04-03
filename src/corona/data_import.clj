(ns corona.data-import
  (:require
   [clj-http.client :as http]
   [ring.util.codec :refer [form-encode]]
   [corona.client :as client])
  (:import
   (org.apache.solr.handler.dataimport
    DataImportHandler
    EventListener)))

;; Docs source: https://smarttechie.org/2014/01/30/how-to-work-with-apache-solr-rest-apis/

(defn make-data-import-base-url
  [client-config & [trailing-uri]]
  (client/create-client-url client-config (str "/dataimport" trailing-uri)))

(defn full-import!
  "This will start the new indexing thread to index the data.
  :clean – The default value is false. This tells whether to clean up the index before the indexing is started.
  :commit – The default value is true. This tells whether to commit the index after the operation.
  :debug – The default value is false. This is helpful to understand what is going during the indexing.
  :entity – This tells the Solr to index which entity to index. If nothing is passed all the entities are executed.
  :optimize – The default value is true. This tells whether to optimize the index after the operation.
  "
  [client-config {:keys [clean commit debug entity optimize] :as params}]
  (let [uri (cond-> "?command=full-import" 
              (boolean? clean)    (str "&clean="    (str clean))
              (boolean? commit)   (str "&commit="   (str commit))
              (boolean? debug)    (str "&debug="    (str debug))
              entity              (str "&entity="   (name entity))
              (boolean? optimize) (str "&optimize=" (str optimize)))]
    (http/get (make-data-import-base-url client-config uri))))

(defn delta-import!
  "This will the changes and index only the changes happened from the last full-import.
  :clean – The default value is false. This tells whether to clean up the index before the indexing is started.
  :commit – The default value is true. This tells whether to commit the index after the operation.
  :debug – The default value is false. This is helpful to understand what is going during the indexing.
  :entity – This tells the Solr to index which entity to index. If nothing is passed all the entities are executed.
  :optimize – The default value is true. This tells whether to optimize the index after the operation.
  "
  [client-config {:keys [clean commit debug entity optimize] :as params}]
  (let [uri (cond-> "?command=delta-import" 
              (boolean? clean)    (str "&clean="    (str clean))
              (boolean? commit)   (str "&commit="   (str commit))
              (boolean? debug)    (str "&debug="    (str debug))
              entity              (str "&entity="   (name entity))
              (boolean? optimize) (str "&optimize=" (str optimize)))]
    (http/get (make-data-import-base-url client-config uri))))

(defn abort!
  "Aborts the running process. Useful to stop indexing process."
  [client-config]
  (http/get (make-data-import-base-url client-config "?command=abort")))

(defn reload-config!
  "Reloads the configuration, catching changes to it without the need to restart Solr."
  [client-config]
  (http/get (make-data-import-base-url client-config "?command=reload-config")))

(defn status
  "Returns the statistics on number of documents indexed, no of documents deleted etc."
  [client-config]
  (http/get (make-data-import-base-url client-config "?command=status")))
