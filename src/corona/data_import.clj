(ns corona.data-import
  (:require
   [jsonista.core :as json]
   [corona.utils :as utils]
   [cuerdas.core :as cstr]
   [org.httpkit.client :as http]
   [ring.util.codec :refer [form-encode]]))

;; Docs source: https://smarttechie.org/2014/01/30/how-to-work-with-apache-solr-rest-apis/

(defn make-data-import-base-url
  [client-config & [trailing-uri]]
  (utils/create-client-url client-config (str "/dataimport" trailing-uri)))

(defn full-import!
  "This will start the new indexing thread to index the data.
  :clean – The default value is false. This tells whether to clean up the index before the indexing is started.
  :commit – The default value is true. This tells whether to commit the index after the operation.
  :debug – The default value is false. This is helpful to understand what is going during the indexing.
  :entity – This tells the Solr to index which entity to index. If nothing is passed all the entities are executed.
  :optimize – The default value is true. This tells whether to optimize the index after the operation.
  "
  [client-config {:keys [clean commit debug entity optimize] :as settings}]
  (let [url (make-data-import-base-url client-config)
        options {:query-params (merge settings {:command "full-import"})
                 :as :auto}]
    (-> @(http/get url options) :body utils/json-read-str)))

(defn delta-import!
  "This will the changes and index only the changes happened from the last full-import.
  :clean – The default value is false. This tells whether to clean up the index before the indexing is started.
  :commit – The default value is true. This tells whether to commit the index after the operation.
  :debug – The default value is false. This is helpful to understand what is going during the indexing.
  :entity – This tells the Solr to index which entity to index. If nothing is passed all the entities are executed.
  :optimize – The default value is true. This tells whether to optimize the index after the operation.
  "
  [client-config {:keys [clean commit debug entity optimize] :as settings}]
  (let [url (make-data-import-base-url client-config)
        options {:query-params (merge settings {:command "delta-import"})
                 :as :auto}]
    (-> @(http/get url options) :body utils/json-read-str)))

(defn abort!
  "Aborts the running process. Useful to stop indexing process."
  [client-config]
  (let [url (make-data-import-base-url client-config)
        options {:query-params {:command "abort"}
                 :as :auto}]
    (-> @(http/get url options) :body utils/json-read-str)))

(defn reload-config!
  "Reloads the configuration, catching changes to it without the need to restart Solr."
  [client-config]
  (let [url (make-data-import-base-url client-config)
        options {:query-params {:command "reload-config"}
                 :as :auto}]
    (-> @(http/get url options) :body utils/json-read-str)))

(defn status
  "Returns the statistics on number of documents indexed, no of documents deleted etc."
  [client-config]
  (let [url (make-data-import-base-url client-config)
        options {:query-params {:command "status"}
                 :as :auto}
        resp @(http/get url options)]
    (update resp :body json/read-value (json/object-mapper {:encode-key-fn #(-> % cstr/kebab keyword)}))))
