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
  ":core <string>: The name of one of the cores to be removed.
  :delete-index? <bool>: If true, will remove the index when unloading the core.
  :delete-data-dir? <bool>: If true, removes the data directory and all sub-directories.
  :delete-instance-dir? <bool>: If true, removes everything related to the core, including the index directory, configuration files and other related files.
  :async <string>: Request ID to track this action which will be processed asynchronously
  "
  [client-config & [{:keys [core delete-index? delete-data-dir? delete-instance-dir? async]}]]
  (let [core-name (name (or core (:core client-config)))
        uri (cond-> (str "/cores?action=UNLOAD&core=" core-name)
              delete-index? (str "&deleteIndex=true")
              delete-data-dir? (str "&deleteDataDir=true")
              delete-instance-dir? (str "&deleteInstanceDir=true")
              async (str "&async=" async))]
    (-> (create-admin-url client-config uri)
        (http/get {:throw-exceptions false
                   :content-type     :json
                   :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn get-core-status
  [client-config & [{:keys [core index-info?]}]]
  (let [core-name (name (or core (:core client-config)))
        uri (cond-> (str "/cores?action=STATUS&core=" core-name)
              (false? index-info?) (str "&indexInfo=false"))]
    (-> (create-admin-url client-config uri)
        (http/get {:throw-exceptions false
                   :content-type     :json
                   :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn get-core-status-details
  "Gets core status and returns only core details map.
  Note: if core doesn't exist, it will return nil."
  [client-config & [{:keys [core index-info?]}]]
  (let [core (or core (:core client-config))]
    (-> (get-core-status client-config)
        :status
        core
        not-empty)))

(comment
  ;; Http Solr Example
  (def client (create-client {:type :http :core :tmdb}))
  ;; Embedded Solr Example
  (def client (create-client {:type :embedded :core :tmdb}))
  (create-core! {:type :http :core :tmdb})
  (delete-core! {:type :http :core :tmdb})
  (get-core-status-details {:type :http :core :item})
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

(defn query-handler
  [client-config handler settings]
  (let [handler-uri (str "/" (name handler))
        params (query/format-params settings)]
    (-> (create-client-url client-config handler-uri)
        (http/get {:query-params params
                   :throw-exceptions false
                   :content-type     :json
                   :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn query-term-vectors
  [client-config settings]
  (query-handler
   client-config
   :tvrh
   (merge query/default-term-vectors-settings settings)))

(defn query
  "Makes and executes solr query from setting map
  Uses solr /select route.
  Returns decoded response of solr service."
  ([settings]
   (query *client* settings))
  ([^SolrClient client settings]
   (->clojure (.query client (query/create-solr-params settings)))))


(defn query-mlt
  "A MoreLikeThis query that uses MLT request handler (/mlt route) to give back
  similar results to a matching document identified in the query under :q
  (e.g. {:q id:12345}.)

  From the specified document, MLT handler will build a query behind the scenes,
  by searching for 'interesting terms' from fields specified under :fl key.

  PriorityQueue is used to fetch the scores for all the terms, which are then
  added as boost queries to a large set of terms in a boolean query, where each
  term is set to SHOULD occur. That way the terms are boosted based on MLT
  semantics, while it uses the ClassicSimilarity behind the scenes.

  These values will be used to build the boost term queries:
  tq = new BoostQuery(tq, boostFactor * myScore / bestScore); 
   e.g. Queue = Term1:100 , Term2:50, Term3:20, Term4:10 
   => Term1:10 , Term2:5, Term3:2, Term4:1 

  settings map:

  :q
  Query terms, defaults to '*:*', or everything.

  :fq
  Filter query, this does not affect the search, only what gets returned

  :mlt.fl
  The fields to use for similarity. DEFAULT_FIELD_NAMES = \"contents\"
  NOTE: if possible use stored TermVectors in the managedschema file for fields
  (e.g. <field name=\"cat\" ... termVectors=\"true\" />)
  If termVectors are not stored, MoreLikeThis will generate terms from stored fields.

  :mlt.mintf
  Minimum Term Frequency - the frequency below which terms will be
  ignored in the source doc. DEFAULT_MIN_TERM_FREQ = 2
  NOTE: Getting good MLT results require some fine-tuning based on experimentation,
  in particular mlt.mintf. Start low and slowly increase until you start getting
  results that \"feel right\".

  :mlt.mindf
  Minimum Document Frequency - the frequency at which words will be
  ignored which do not occur in at least this many docs. DEFAULT_MIN_DOC_FREQ = 5

  :mlt.minwl
  Minimum word length below which words will be ignored. DEFAULT_MIN_WORD_LENGTH = 0

  :mlt.maxwl
  Maximum word length above which words will be ignored. DEFAULT_MAX_WORD_LENGTH = 0

  :mlt.maxqt
  Maximum number of query terms that will be included in any generated query.
  DEFAULT_MAX_QUERY_TERMS = 25

  :mlt.maxntp
  Maximum number of tokens to parse in each example doc field that is not stored
  with TermVector support. DEFAULT_MAX_NUM_TOKENS_PARSED = 5000

  :mlt.boost
  [true/false] set if the query will be boosted by the interesting term relevance.
  DEFAULT_BOOST = false

  :mlt.qf
  Query fields and their boosts using the same format as that used in
  DisMaxQParserPlugin. These fields must also be specified in mlt.fl.

  :mlt.match.include
  Specifies whether or not the response should include the matched document
  under :match key. Default: true

  :mlt.match.offset
  Specifies an offset into the main query search results to locate the document
  on which the MoreLikeThis query should operate. By default, the query operates
  on the first result for the q parameter.

  :mlt.interestingTerms
  Controls how the MoreLikeThis component presents the \"interesting\" terms
  (the top TF/IDF terms) for the query. Supports three values.
  - \"list\" : lists the terms.
  - \"none\" : lists no terms.
  - \"details\": lists the terms along with the boost value used for each term.
  Unless mlt.boost=true, all terms will have boost=1.0.

  :fl
  Fields to return. We force 'id' to be returned so that there is a unique
  identifier with each record.

  :wt
  Data type returned, defaults to 'json'

  :start
  Record to start at, default to beginning.

  :rows
  Number of records to return. Defaults to 10.
  "
  ([settings]
   (query-mlt *client* settings))
  ([^SolrClient client settings]
   (->clojure (.query client (query/create-mlt-solr-params settings)))))


(defn query-mlt-edismax
  "Like more like this handler query or `query-mlt` but allows edismax params
  (e.g. `:boost` `:bf` `:bq` `:qf`)

  This query handler runs a MLT query then passes boosted interesting terms
  to normal edismax query `(query client {:defType \"edismax\" ...})`

  Special settings:

  :mlt.q
  To reach the matching document to get interesting terms for.

  :mlt.boost.factor
  to globally change mlt.fl boosts.

  NOTE: To better understand boosting methods, see
  https://nolanlawson.com/2012/06/02/comparing-boost-methods-in-solr/
  "
  [client settings]
  (let [mlt-q (:mlt.q settings)
        mlt-settings (when mlt-q (query/build-internal-mlt-settings settings))
        mlt-resp (when mlt-q (query-mlt client mlt-settings))
        mlt-terms (cond-> (query/mlt-resp->terms mlt-resp)
                    (:mlt.boost settings) (query/boost-terms
                                           (:mlt.qf settings)
                                           (:mlt.boost.factor settings)))
        q (query/mlt-terms->q mlt-q mlt-terms (:q settings))
        settings (-> settings
                     (assoc :q q)
                     (dissoc query/mlt-keys)
                     (dissoc :mlt.boost.factor :mlt.qf.raw :mlt.boost.factor.raw))
        resp (query client (merge {:defType "edismax"} settings))]
    (assoc resp :interestingTerms mlt-terms :match (:match mlt-resp))))


(defn query-mlt-tv-edismax
  "Like more like this handler query or `query-mlt` but

  - takes top-k terms *PER FIELD*, for more explanations, see
    https://github.com/DiceTechJobs/RelevancyFeedback#isnt-this-just-the-mlt-handler

  - allows edismax params (e.g. `:boost` `:bf` `:bq` `:qf`)
    NOTE: To better understand boosting methods, see
    https://nolanlawson.com/2012/06/02/comparing-boost-methods-in-solr/

  Special settings:

  :mlt.q
  To reach the matching document to get interesting terms.

  Supported mlt keys: :mlt-fl, :mlt-qf

  IMPORTANT: All mlt.fl fields MUST be set as TermVectors=true in the managedschema
  for the mlt query to be integrated to main q.
  "
  [client-config settings]
  (let [mlt-q (:mlt.q settings)
        tv-resp (query-term-vectors
                 client-config
                 {:q mlt-q
                  :fl (:mlt.fl settings)})
        tv-terms (query/term-vectors-resp->interesting-terms-per-field
                  tv-resp
                  (:mlt.qf settings))
        q (query/tv-terms->q mlt-q tv-terms (:q settings))
        settings (-> settings
                     (assoc :q q)
                     (dissoc query/mlt-keys)
                     (dissoc :mlt.q))
        client (create-client client-config)
        resp (query client (merge {:defType "edismax"} settings))]
    (assoc resp :interestingTerms tv-terms :match (-> tv-resp :response))))
