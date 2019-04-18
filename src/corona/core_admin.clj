(ns corona.core-admin
  (:require
   [clojure.data.json :as json]
   [corona.utils :as utils]
   [org.httpkit.client :as http]))


;;; Core Admin API

;; SOURCE: https://lucene.apache.org/solr/guide/7_7/coreadmin-api.html


(defn create!
  "The CREATE action creates a new core and registers it.

  If a Solr core with the given name already exists, it will continue to handle
  requests while the new core is initializing. When the new core is ready, it
  will take new requests and the old core will be unloaded.

  Settings:

  :name <string>
  The name of the new core. Same as name on the <core> element. This parameter
  is required.

  :instanceDir <string>
  The directory where files for this core should be stored. Same as instanceDir
  on the <core> element. The default is the value specified for the name
  parameter if not supplied.

  :config <string>, default: \"solrconfig.xml\"
  Name of the config file relative to instanceDir.

  :schema <string>
  Name of the schema file to use for the core. Please note that if you are using
  a \"managed schema\" (the default behavior) then any value for this property
  which does not match the effective managedSchemaResourceName will be read
  once, backed up, and converted for managed schema use. See Schema Factory
  Definition in SolrConfig for details.

  :dataDir <string>
  Name of the data directory relative to instanceDir.

  :configSet
  Name of the configset to use for this core. For more information, see the
  section Config Sets.

  :collection
  :collection.param
  :collection.configName
  The name of the collection to which this core belongs. The default is the
  name of the core. collection.param=value causes a property of param=value
  to be set if a new collection is being created.
  Use collection.configName=config-name to point to the configuration for a new
  collection.
  NOTE:While it’s possible to create a core for a non-existent collection,
  this approach is not supported and not recommended. Always create a
  collection using the Collections API before creating a core directly for it.

  :shard
  The shard id this core represents. Normally you want to be auto-assigned a
  shard id.

  :property.name
  Sets the core property name to value. See the section on defining
  core.properties file contents.

  :async
  Request ID to track this action which will be processed asynchronously.
  Use collection.configName=configname to point to the config for a new
  collection.
  "
  [client-config & [{:keys [core] :as settings}]]
  (let [core-name (name (or core (:core client-config)))
        query-params (merge settings {:action "CREATE" :name core-name})
        options {:query-params query-params
                 :timeout 10000
                 :as :auto}
        url (utils/create-admin-url client-config "/cores")
        {:keys [body]} @(http/get url options)]
    (when body (json/read-str body :key-fn keyword))))


(defn delete!
  ":core <string or keyword>
  The name of a core to be removed. This parameter is required.

  :deleteIndex <bool>, default: false
  If true, will remove the index when unloading the core.

  :deleteDataDir <bool>, default: false
  If true, removes the data directory and all sub-directories.
  false.

  :deleteInstanceDir <bool>, default: false
  If true, removes everything related to the core, including the index directory, configuration files and other related files.

  :async
  Request ID to track this action which will be processed asynchronously.
  "
  [client-config & [{:keys [core] :as settings}]]
  (let [core-name (name (or core (:core client-config)))
        query-params (merge settings {:action "UNLOAD" :core core-name})
        options {:query-params query-params
                 :timeout 10000
                 :as :auto}
        url (utils/create-admin-url client-config "/cores")
        {:keys [body]} @(http/get url options)]
    (when body (json/read-str body :key-fn keyword))))


(defn status
  "The STATUS action returns the status of all running Solr cores, or status for only the named core.

  Settings:

  :core
  The name of a core, as listed in the \"name\" attribute of a <core> element
  in solr.xml.

  :indexInfo
  If false, information about the index will not be returned with a core STATUS
  request. In Solr implementations with a large number of cores (i.e., more
  than hundreds), retrieving the index information for each core can take a lot
  of time and isn’t always required. The default is true.
  "
  [client-config & [{:keys [core] :as settings}]]
  (let [core-name (name (or core (:core client-config)))
        query-params (merge settings {:action "STATUS" :core core-name})
        options {:query-params query-params
                 :as :auto}
        url (utils/create-admin-url client-config "/cores")
        {:keys [body]} @(http/get url options)]
    (when body (json/read-str body :key-fn keyword))))


(defn status-details
  "Gets core status and returns only core details map.
  Note: if core doesn't exist, it will return nil."
  [client-config & [{:keys [core index-info?]}]]
  (let [core (or core (:core client-config))]
    (-> (status client-config)
        :status
        core
        not-empty)))


