(ns corona.client
  (:refer-clojure :exclude [reset!])
  (:require
   [clj-http.client :as http]
   [clojure.data.csv :as csv]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [corona.query :as query]
   [clojure.string :as string]))


;;; Client (connexion to server)

(def default-http-config
  "Needs a custom :core value"
  {:type :http
   :host "127.0.0.1" ;"localhost"
   :port 8983
   :path "/solr"
   ;;:core ""
   })

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


;;; Index Updates
;; Index Handlers are Request Handlers designed to add, delete and update
;; documents to the index. In addition to having plugins for importing rich
;; documents using Tika or from structured data sources using the Data Import
;; Handler, Solr natively supports indexing structured documents with the
;; following API.
;; Source: https://lucene.apache.org/solr/guide/7_6/uploading-data-with-index-handlers.html#json-formatted-index-updates

(defn update!
  "Sends JSON Update Commands.
  In general, the JSON update syntax supports all of the update commands that
  the XML update handler supports, through a straightforward mapping. Multiple
  commands, adding and deleting documents, may be contained in one message.

  Usage:

  (update! client-config {:delete {:id \"id1\"}})

  (update! client-config {:add {:commitWithin 5000,
                                :overwrite false
                                :doc {:f1 \"v1\"
                                      :f2 \"v2\"}}})

  Source: https://lucene.apache.org/solr/guide/6_6/uploading-data-with-index-handlers.html#UploadingDatawithIndexHandlers-SendingJSONUpdateCommands
  "
  [client-config settings]
  (let [uri (cond-> "/update")]
    (-> (create-client-url client-config "/update")
        (http/post {:throw-exceptions false
                    :body             (json/write-str settings)
                    :content-type     :json
                    :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn add!
  "Uploads 'doc-or-docs' (map or vector of maps) to solr using opened 'client'.
  Docs uploaded in pending status, do not auto commit unless mentioned in settings.
  Returns decoded response of service.

  Usage:

  (add! client-config {:id \"1\"  :title \"title 1\"})
  (add! client-config [{:id \"1\" :title \"title 1\"}
                       {:id \"2\" :title \"title 2\"}])
  (add!
   client-config
   [{:id \"1\" :title \"title 1\"}
    {:id \"2\" :title \"title 2\"}]
   {:commit true})

  Settings:

  :commit <bool>, default true
  The :commit operation writes all documents loaded since the last commit to
  one or more segment files on the disk. Before a commit has been issued,
  newly indexed content is not visible to searches. The commit operation opens
  a new searcher, and triggers any event listeners that have been configured.
  Commits may be triggered from <autocommit> parameters in solrconfig.xml.

  :commitWithin <int>
  Add the document within the specified number of milliseconds.

  :optimize <bool>
  The :optimize operation requests Solr to merge internal data structures in
  order to improve search performance. For a large index, optimization will
  take some time to complete, but by merging many small segment files into
  a larger one, search performance will improve. If you are using Solr’s
  replication mechanism to distribute searches across many systems, be aware
  that after an optimize, a complete index will need to be transferred.
  In contrast, post-commit transfers are usually much smaller.

  :overwrite <bool>, default: true
  Indicates if the unique key constraints should be checked to overwrite
  previous versions of the same document (see below).
  "
  [client-config doc-or-docs & [settings]]
  (let [docs (if (sequential? doc-or-docs) doc-or-docs [doc-or-docs])]
    (-> (create-client-url client-config "/update")
        (http/post {:query-params settings
                    :throw-exceptions false
                    :body             (json/write-str docs)
                    :content-type     :json
                    :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn delete!
  "Usage:
  (delete! client-config \"id1\")
  (delete! client-config [\"id2\" \"id3\"])
  (delete! client-config {:query \"*:*\"})
  (delete! client-config [\"id2\" \"id3\"] {:commit true})"
  [client-config id-ids-or-query-map & [settings]]
  (let [body {:delete id-ids-or-query-map}]
    (-> (create-client-url client-config "/update")
        (http/post {:query-params settings
                    :throw-exceptions false
                    :body             (json/write-str body)
                    :content-type     :json
                    :accept           :json})
        :body
        (json/read-str :key-fn keyword))))

(defn commit!
  "The :commit operation writes all documents loaded since the last commit to
  one or more segment files on the disk. Before a commit has been issued,
  newly indexed content is not visible to searches. The commit operation opens
  a new searcher, and triggers any event listeners that have been configured.
  Commits may be triggered from <autocommit> parameters in solrconfig.xml.

  Optional Settings:

  :waitSearcher <bool>, default: true
  Blocks until a new searcher is opened and registered as the main query searcher, making the changes visible.

  :expungeDeletes <bool>, default: false
  (commit only) Merges segments that have more than 10% deleted docs, expunging them in the process.
  "
  [client-config & [settings]]
  (update! client-config {:commit (or settings {})}))

(defn optimize!
  "The :optimize operation requests Solr to merge internal data structures in
  order to improve search performance. For a large index, optimization will
  take some time to complete, but by merging many small segment files into
  a larger one, search performance will improve. If you are using Solr’s
  replication mechanism to distribute searches across many systems, be aware
  that after an optimize, a complete index will need to be transferred.
  In contrast, post-commit transfers are usually much smaller.
  
  Optional Settings:

  :waitSearcher <bool>, default: true
  Blocks until a new searcher is opened and registered as the main query searcher, making the changes visible.

  :maxSegments <int>, default: 1
  (optimize only) Merges the segments down to no more than this number of segments.
  "
  [client-config bool-or-settings]
  (update! client-config {:commit bool-or-settings}))


(defn clear-index!
  "Deletes all documents in the index by 'client'.
  Returns decoded response of solr service.
  NOTE: needs explicit (commit! client-config) after it"
  [client-config & [settings]]
  (delete! client-config {:query "*:*"} settings))

(defn reset!
  "Clears the index and uploads provided doc or docs (map or maps).
  Returns solr service decoded response.
  NOTE: needs explicit (commit! client) after it"
  [client-config doc-or-docs & [settings]]
  (clear-index! client-config settings)
  (add! client-config doc-or-docs settings))


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
  Returns decoded response of solr service.
  "
  [client-config settings]
  (query-handler
   client-config
   :select
   settings))


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

  :q <string> default: \"*:*\" (everything)
  Query terms

  :fq
  Filter query, this does not affect the search, only what gets returned

  :mlt.fl <string>, default: \"contents\"
  The fields to use for similarity. 
  NOTE: if possible use stored TermVectors in the managedschema file for fields
  (e.g. <field name=\"cat\" ... termVectors=\"true\" />)
  If termVectors are not stored, MoreLikeThis will generate terms from stored fields.

  :mlt.mintf <int>, default: 2
  Minimum Term Frequency - the frequency below which terms will be
  ignored in the source doc. 
  NOTE: Getting good MLT results require some fine-tuning based on experimentation,
  in particular mlt.mintf. Start low and slowly increase until you start getting
  results that \"feel right\".

  :mlt.mindf <int>, default: 5
  Minimum Document Frequency - the frequency at which words will be
  ignored which do not occur in at least this many docs.

  :mlt.minwl <int>, default: 0
  Minimum word length below which words will be ignored.

  :mlt.maxwl <int>, default: 0
  Maximum word length above which words will be ignored.

  :mlt.maxqt <int>, default: 25
  Maximum number of query terms that will be included in any generated query.

  :mlt.maxntp <int>, default: 5000
  Maximum number of tokens to parse in each example doc field that is not stored
  with TermVector support.

  :mlt.boost <bool>, default: false
  [true/false] set if the query will be boosted by the interesting term relevance.
  
  :mlt.qf
  Query fields and their boosts using the same format as that used in
  DisMaxQParserPlugin. These fields must also be specified in mlt.fl.

  :mlt.match.include <bool>, default: true
  Specifies whether or not the response should include the matched document
  under :match key.

  :mlt.match.offset
  Specifies an offset into the main query search results to locate the document
  on which the MoreLikeThis query should operate. By default, the query operates
  on the first result for the q parameter.

  :mlt.interestingTerms <[\"list\", \"none\", \"details\"]>
  Controls how the MoreLikeThis component presents the \"interesting\" terms
  (the top TF/IDF terms) for the query. Supports three values.
  - \"list\" : lists the terms.
  - \"none\" : lists no terms.
  - \"details\": lists the terms along with the boost value used for each term.
  Unless mlt.boost=true, all terms will have boost=1.0.

  :fl
  Fields to return. We force 'id' to be returned so that there is a unique
  identifier with each record.

  :wt <enum>, default: \"json\"
  Data type returned.

  :start <int>, default: 0
  Record to start at

  :rows <int>, default: 10
  Number of records to return.
  "
  [client-config settings]
  (query-handler
   client-config
   :mlt
   settings))

(defn query-mlt-tv-edismax
  "Like more like this handler query or `query-mlt` but

  - takes top-k terms *PER FIELD*, for more explanations, see
    https://github.com/DiceTechJobs/RelevancyFeedback#isnt-this-just-the-mlt-handler

  - allows edismax params (e.g. `:boost` `:bf` `:bq` `:qf`)
    NOTE: To better understand boosting methods, see
    https://nolanlawson.com/2012/06/02/comparing-boost-methods-in-solr/

  Special settings:

  :mlt.q <string>
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
        resp (query client-config (merge {:defType "edismax"} settings))]
    (assoc resp :interestingTerms tv-terms :match (-> tv-resp :response))))
