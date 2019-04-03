(ns corona.client
  (:refer-clojure :exclude [reset!])
  (:require
   [clj-http.client :as http]
   [clojure.data.csv :as csv]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [corona.conversion :refer [->clojure]]
   [corona.query :as query]
   [corona.utils :as utils])
  (:import
   (java.io Writer File)
   (java.security InvalidParameterException)
   (java.util Collection HashMap)
   (org.apache.commons.io FileUtils)
   (org.apache.solr.client.solrj SolrClient)
   (org.apache.solr.client.solrj.embedded EmbeddedSolrServer)
   (org.apache.solr.client.solrj.impl HttpSolrClient$Builder)
   (org.apache.solr.common SolrInputDocument)
   (org.apache.solr.core CoreContainer SolrResourceLoader)))


;;; Client (connexion to server)

(declare ^:dynamic *client*)

#_(ns soleil.client
  ^{:doc "A clojure library for Apache Solr." :author "Matt Lehman"}
  (:import [java.io File]
           [org.apache.solr.client.solrj.impl CommonsHttpSolrServer]))

(def default-embedded-config
  {:type :embedded
   :solr-config "solr.xml"
   :dir (or (System/getenv "SOLR_HOME")
            (str (System/getProperty "user.home") "/solr-7.6.0")
            "./solr")
   :connection-timeout 10000
   :socket-timeout     60000
   ;;:core ""
   })

(def default-http-config
  {:type :http
   :host "127.0.0.1" ;"localhost"
   :port 8983
   :path "/solr"
   ;;:core ""
   })

(defmulti create-client* :type)

(defn create-client-url
  "Usage:
  (create-client-url {:host \"localhost\" :port 8983 :path \"/solr\" :core :tmdb})
  ;=>  http://localhost:8983/solr/tmdb
  "
  [config & [uri]]
  (let [{:keys [host port path core]} (merge default-http-config config)]
    (str "http://" host ":" port path (when core (str "/" (name core))) uri)))

(defn create-admin-url
  [config & [uri]]
  (create-client-url (assoc config :core :admin) uri))

(defmethod create-client* :http [config]
  (let [{:keys [connection-timeout socket-timeout] :as config}
        (merge default-http-config config)]
    (-> (create-client-url config)
        (HttpSolrClient$Builder.)
        (.withConnectionTimeout (or connection-timeout 10000))
        (.withSocketTimeout (or socket-timeout 60000))
        (.build))))

;; The EmbeddedSolrServer class provides an implementation of the
;; SolrClient client API talking directly to an micro-instance of
;; Solr running directly in your Java application.

;; The embedded server is recommended when you need a simple solution
;; that is not distributed (e.g. unit and integration tests, development
;; databases)

;; Please note:
;; If you don't now if you need a dedicated server fell free to start
;; with the embedded version and enjoy simplified configuration and
;; setup and a minor performance increase.
;; Once you need a stand alone server, just change some lines of code
;; and the rest stays the same.

(defmethod create-client* :embedded [config]
  (let [{:keys [dir core]} (merge default-embedded-config config)
        container (CoreContainer. dir)]
    (EmbeddedSolrServer. container (name core))))

(defmethod create-client* :http [config]
  (let [{:keys [connection-timeout socket-timeout] :as config}
        (merge default-http-config config)]
    (-> (create-client-url config)
        (HttpSolrClient$Builder.)
        (.withConnectionTimeout (or connection-timeout 10000))
        (.withSocketTimeout (or socket-timeout 60000))
        (.build))))

(defmethod create-client* :default [config]
  (create-client* (assoc config :type :http)))

(defn ^SolrClient create-client
  "Constructs a SolrClient with a configuration map.
    :type - The implementation of SolrClient to create.
   Additional parameters depending on type:
    :http - CommonsHttpSolrServer
     :host - The base URL of the Solr server. Defaults to 127.0.0.1.
     :port - The port of the Solr server. Defaults to 8983.
     :path - The path to the Solr to the index. Defaults to SOLR_HOME env value or '/solr'.
     :core - Solr Core collection
    :embedded - EmbeddedSolrServer
     :solr-config - The solr configuration file. Defaults to 'solr.xml'.
     :dir - The directory path. Defaults to './solr'
     :core - Solr Core collection"
  [conf]
  (create-client* conf))

(defmacro with-client [^SolrClient client & body]
  `(binding [*client* ~client]
     ~@body))

;;; Core Admin

;; TODO: extend API with all opts, see https://lucene.apache.org/solr/guide/7_6/coreadmin-api.html
(defn create-core!
  [client-config & [{:keys [core instance-dir]}]]
  (let [core-name (name (or core (:core client-config)))
        uri (cond-> (str "/cores?action=CREATE&name=" core-name)
              instance-dir (str "&instanceDir=" instance-dir))]
    (-> (create-admin-url client-config uri)
        (http/get {:throw-exceptions false
                   :content-type     :json
                   :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn delete-core!
  [client-config & [{:keys [core delete-index?]}]]
  (let [core-name (name (or core (:core client-config)))
        uri (cond-> (str "/cores?action=UNLOAD&core=" core-name)
              delete-index? (str "&deleteIndex=true"))]
    (-> (create-admin-url client-config uri)
        (http/get {:throw-exceptions false
                   :content-type     :json
                   :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(comment
  ;; Http Solr Example
  (def client (create-client {:type :http :core :tmdb}))
  ;; Embedded Solr Example
  (def client (create-client {:type :embedded :core :tmdb}))
  (create-core! {:type :http :core :tmdb})
  (delete-core! {:type :http :core :tmdb})
  )

;;; Update

;; this prevents ugly error "Field is not compatible to map entry" while printing doc

(defn commit!
  "Commits documents uploaded by 'client'
  and returns solr service decoded response.
  NOTE: this should be added at the end of any update operation"
  ([]
   (commit! *client*))
  ([^SolrClient client]
   (->clojure (.commit client))))


(defmethod print-method SolrInputDocument [doc ^Writer w]
  (.write w (str "#object[org.apache.solr.common.SolrInputDocument "
                 (.values doc)
                 ">")))

(defn create-doc!
  "Creates solr document from generic 'document-map'.
  Returns SolrInputDocument object."
  ^SolrInputDocument [document-map]
  (reduce-kv (fn [^SolrInputDocument doc k v]
               (cond
                 (map? v)
                 (throw (UnsupportedOperationException.
                         (str "Field " k " has unsupported value: " (pr-str v))))

                 (seq? v)
                 (if (empty? v)
                   doc
                   (doto doc
                     (.addField (name k) (into-array (-> v first type) v))))

                 :else (doto doc (.addField (name k) v))))
             (SolrInputDocument. (make-array String 0))
             document-map))

(defn add!
  "Uploads 'doc-or-docs' (map or vector of maps) to solr using opened 'client'.
  Docs uploaded in pending status, but could be auto committed depending on solr settings.
  Returns decoded response of service.
  NOTE: needs explicit (commit! client) after it"
  ([doc-or-docs]
   (add! *client* doc-or-docs))
  ([^SolrClient client doc-or-docs]
   (if (map? doc-or-docs)
     (->clojure (.add client (create-doc! doc-or-docs)))
     (->clojure (.add client ^Collection (mapv create-doc! doc-or-docs))))))

(defn delete!
  "Deletes documents satisfying solr query (just assoc 'options' :q 'query') by 'client'.
  Returns decoded response of solr service."
  ([^String q]
   (delete! *client* q))
  ([^SolrClient client ^String q]
   (->clojure (.deleteByQuery client q))))

(defn clear-index!
  "Deletes all documents in the index by 'client'.
  Returns decoded response of solr service.
  NOTE: needs explicit (commit! client) after it"
  ([]
   (clear-index! *client*))
  ([^SolrClient client]
   (delete! client "*:*")))

(defn reset!
  "Clears the index and uploads provided doc or docs (map or maps).
  Returns solr service decoded response.
  NOTE: needs explicit (commit! client) after it"
  ([doc-or-docs]
   (reset! *client* doc-or-docs))
  ([^SolrClient client doc-or-docs]
   (clear-index! client)
   (add! client doc-or-docs)))


;;; Query

(defn query
  "Makes and executes solr query from query-map
  Uses solr /select route.
  Returns decoded response of solr service."
  ([query-map]
   (query *client* query-map))
  ([^SolrClient client query-map]
   (->clojure (.query client (query/create-solr-params query-map)))))

(defn query-mlt
  "Makes and executes solr query from query-map
  Uses solr /mlt route.
  Returns decoded response of solr service."
  ([query-map]
   (query-mlt *client* query-map))
  ([^SolrClient client query-map]
   (->clojure (.query client (query/create-mlt-solr-params query-map)))))
