(ns corona.index
  (:require
   [clojure.data.json :as json]
   [corona.utils :as utils]
   [org.httpkit.client :as http]))


;;; Index Handler API

;; SOURCE: https://lucene.apache.org/solr/guide/7_6/uploading-data-with-index-handlers.html#json-formatted-index-updates

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
  (let [url (utils/create-client-url client-config "/update")
        options {:throw-exceptions false
                 :body             (json/write-str settings)
                 :headers          {"Content-Type" "application/json"}
                 :as               :auto}]
    (-> @(http/post url options) :body utils/json-read-str)))

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
  (let [docs (if (sequential? doc-or-docs) doc-or-docs [doc-or-docs])
        url (utils/create-client-url client-config "/update")
        options {:query-params settings
                 :body         (json/write-str docs)
                 :headers      {"Content-Type" "application/json"}
                 :as           :auto}]
    (-> @(http/post url options) :body utils/json-read-str)))

(defn delete!
  "Usage:
  (delete! client-config \"id1\")
  (delete! client-config [\"id2\" \"id3\"])
  (delete! client-config {:query \"*:*\"})
  (delete! client-config [\"id2\" \"id3\"] {:commit true})"
  [client-config id-ids-or-query-map & [settings]]
  (let [body {:delete id-ids-or-query-map}
        url (utils/create-client-url client-config "/update")
        options {:query-params settings
                 :body         (json/write-str body)
                 :headers      {"Content-Type" "application/json"}
                 :as           :auto}]
    (-> @(http/post url options) :body utils/json-read-str)))

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


(defn clear!
  "Deletes all documents in the index by 'client'.
  Returns decoded response of solr service.
  NOTE: needs explicit (commit! client-config) after it"
  [client-config & [settings]]
  (delete! client-config {:query "*:*"} settings))
