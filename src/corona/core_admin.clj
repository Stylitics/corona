(ns corona.core-admin
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [corona.utils :as utils]
   [org.httpkit.client :as http]))


;;; Core Admin API

;; SOURCE: https://lucene.apache.org/solr/guide/7_7/coreadmin-api.html


(defn update!
  "CoreAdmin actions can be executed by specifying an action request
  parameter, with additional action specific arguments provided as
  additional parameters.

  Settings

  :action <\"STATUS\", \"CREATE\", \"RELOAD\", \"RENAME\", \"SWAP\", \"UNLOAD\",
           \"MERGEINDEXES\", \"SPLIT\", \"REQUESTSTATUS\", \"REQUESTRECOVERY\">

  :<additional settings for action>
  "
  [client-config & [{:keys [core] :as settings}]]
  (let [core-name (name (or core (:core client-config)))
        query-params (merge settings {:core core-name})
        options {:query-params query-params
                 :timeout 10000
                 :as :auto}
        url (utils/create-admin-url client-config "/cores")]
    (some-> @(http/get url options) :body utils/json-read-str)))

(defn status
  "The STATUS action returns the status of all running Solr cores, or status for only the named core.

  Settings:

  :core <string or keyword>
  The name of a core, as listed in the \"name\" attribute of a <core> element
  in solr.xml.

  :indexInfo <boolean>, default: true
  If false, information about the index will not be returned with a core STATUS
  request. In Solr implementations with a large number of cores (i.e., more
  than hundreds), retrieving the index information for each core can take a lot
  of time and isn’t always required. The default is true.

  Example:

  (status client-config {:core \"core-name\"})

  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "STATUS")))

(defn status-details
  "Custom Corona Helper.
  It gets core status and returns only core details map.
  Note: if core doesn't exist, it will return nil."
  [client-config & [{:keys [core] :as settings}]]
  (let [core (or core (:core client-config))]
    (-> (status client-config settings)
        :status
        core
        not-empty)))

(defn create!
  "The CREATE action creates a new core and registers it.

  If a Solr core with the given name already exists, it will continue to handle
  requests while the new core is initializing. When the new core is ready, it
  will take new requests and the old core will be unloaded.

  Settings:

  :name <string> *required*
  The name of the new core. Same as name on the <core> element.

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

  Example:

  (create! client-config {:name \"core-name\"
                          :instanceDir \"path/to/dir\"
                          :config \"solrconfig.xml\"
                          :dataDir \"data\"})

  (create! client-config {:name \"my_core\"
                          :collection \"my_collection\"
                          :shard \"shard2\"})
  "
  [client-config & [{:keys [core] :as settings}]]
  (let [core-name (name (or core (:core client-config)))]
    (update! client-config (-> {:name core-name}
                               (merge settings)
                               (assoc :action "CREATE")))))


(defn reload!
  "The RELOAD action loads a new core from the configuration of an existing,
  registered Solr core.
  While the new core is initializing, the existing one will continue to handle
  requests. When the new Solr core is ready, it takes over and the old core is
  unloaded. This is useful when you’ve made changes to a Solr core’s
  configuration on disk, such as adding new field definitions.
  Calling the RELOAD action lets you apply the new configuration without having
  to restart the Web container.

  Settings:

  :core <string or keyword> *required*
  The name of a core to be removed.

  Example:

  (reload! client-config {:core \"core-name\"})
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "RELOAD")))


(defn rename!
  "The RENAME action changes the name of a Solr core.

  Settings:

  :core <string> *required*
  The name of the Solr core to be renamed.

  :other <string> *required*
  The new name for the Solr core. If the persistent attribute of
  <solr> is true, the new name will be written to solr.xml as the
  name attribute of the <core> attribute.

  :async <string>
  Request ID to track this action which will be processed asynchronously

  Example:

  (rename! client-config {:core \"core-name\"
                          :other \"other-core-name\"})
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "RENAME")))

(defn swap!
  "SWAP atomically swaps the names used to access two existing Solr cores.
  This can be used to swap new content into production. The prior core remains
  available and can be swapped back, if necessary. Each core will be known by
  the name of the other, after the swap.

  Settings:

  :core <string> *required*
  The name of one of the cores to be swapped.

  :other <string> *required*
  The name of one of the cores to be swapped.

  :async <string>
  Request ID to track this action which will be processed asynchronously

  Example:

  (swap! client-config {:core \"core-name\"
                        :other \"other-core-name\"})
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "SWAP")))


(defn unload!
  "The UNLOAD action removes a core from Solr. Active requests will continue
  to be processed, but no new requests will be sent to the named core. If a
  core is registered under more than one name, only the given name is removed.

  The UNLOAD action requires a parameter (core) identifying the core to be
  removed. If the persistent attribute of <solr> is set to true, the <core>
  element with this name attribute will be removed from solr.xml.

  NOTE: Unloading all cores in a SolrCloud collection causes the removal of that
  collection’s metadata from ZooKeeper.

  Settings:

  :core <string or keyword> *required*
  The name of a core to be removed.

  :deleteIndex <bool>, default: false
  If true, will remove the index when unloading the core.

  :deleteDataDir <bool>, default: false
  If true, removes the data directory and all sub-directories.
  false.

  :deleteInstanceDir <bool>, default: false
  If true, removes everything related to the core, including the index directory, configuration files and other related files.

  :async
  Request ID to track this action which will be processed asynchronously.

  Example:

  (unload! client-config {:core \"core-name\"})
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "UNLOAD")))

(defn merge-indexes!
  "The MERGEINDEXES action merges one or more indexes to another index.
  The indexes must have completed commits, and should be locked against writes
  until the merge is complete or the resulting merged index may become
  corrupted. The target core index must already exist and have a compatible
  schema with the one or more indexes that will be merged to it. Another commit
  on the target core should also be performed after the merge is complete.

  Settings:

  :core <string> *required*
  The name of the target core/index.

  :indexDir <vector of strings>
  Directories that would be merged.

  :srcCore <vector of strings>
  Source cores that would be merged.

  :async <string>
  Request ID to track this action which will be processed asynchronously

  Example:

  (merge-indexes! client-config {:core \"new-core-name\"
                                 :indexDir [\"path/to/core1/data/index\"
                                            \"path/to/core2/data/index\"]})

  In this example, we use the indexDir parameter to define the index locations
  of the source cores. The core parameter defines the target index. A benefit
  of this approach is that we can merge any Lucene-based index that may not be
  associated with a Solr core.

  Alternatively, we can instead use a srcCore parameter, as in this example:

  (merge-indexes! client-config {:core \"new-core-name\"
                                 :srcCore [\"core1-name\" \"core2-name\"]})

  This approach allows us to define cores that may not have an index path that
  is on the same physical server as the target core. However, we can only use
  Solr cores as the source indexes. Another benefit of this approach is that we
  don’t have as high a risk for corruption if writes occur in parallel with the
  source index.

  We can make this call run asynchronously by specifying the async parameter
  and passing a request-id. This id can then be used to check the status of the
  already submitted task using the REQUESTSTATUS API.
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "MERGEINDEXES")))

(defn split!
  "The SPLIT action splits an index into two or more indexes. The index being
  split can continue to handle requests. The split pieces can be placed into a
  specified directory on the server’s filesystem or it can be merged into
  running Solr cores.

  Settings:

  :core <string> *required*
  The name of the core to be split.

  :path <vector of strings>
  The directory path in which a piece of the index will be written.

  :targetCore <vector of strings>
  The target Solr core to which a piece of the index will be merged

  :ranges <string>
  A comma-separated list of hash ranges in hexadecimal format
  TODO: accept a vector

  :split.key <string>
  The key to be used for splitting the index

  :async <string>
  Request ID to track this action which will be processed asynchronously

  NOTE: Either path or targetCore parameter must be specified but not both.
  The ranges and split.key parameters are optional and only one of the two
  should  be specified, if at all required.

  Example:

  The core index will be split into as many pieces as the number of path
  or targetCore parameters.

  Usage with two targetCore parameters:

  (split! client-config {:core \"core0\"
                         :targetCore [\"core1\" \"core2\"]})

  Here the core index will be split into two pieces and merged into the
  two targetCore indexes.

  Usage with two path parameters:

  (split! client-config {:core \"core0\"
                         :path [\"path/to/index/1\"
                                \"path/to/index/2\"]})

  The core index will be split into two pieces and written into the two
  directory paths specified.

  Usage with the split.key parameter:

  (split! client-config {:core \"core0\"
                         :targetCore \"core1\"
                         :split.key \"A!\"})

  Here all documents having the same route key as the split.key i.e. 'A!'
  will be split from the core index and written to the targetCore.

  Usage with ranges parameter:

  (split! client-config {:core \"core0\"
                         :targetCore [\"core1\" \"core2\" \"core3\"]
                         :ranges [\"0-1f4\" \"1f5-3e8\" \"3e9-5dc\"]})

  This example uses the ranges parameter with hash ranges 0-500, 501-1000
  and 1001-1500 specified in hexadecimal. Here the index will be split into
  three pieces with each targetCore receiving documents matching the hash
  ranges specified i.e. core1 will get documents with hash range 0-500, core2
  will receive documents with hash range 501-1000 and finally, core3 will
  receive documents with hash range 1001-1500. At least one hash range must be
  specified. Please note that using a single hash range equal to a route key’s
  hash range is NOT equivalent to using the split.key parameter because multiple
  route keys can hash to the same range.
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "SPLIT")))

(defn request-status
  "Request the status of an already submitted asynchronous CoreAdmin API call.

  Settings:

  :requestid <string> *required*
  The user defined request-id for the Asynchronous request.

  Example:

  (request-status client-config {:requestid \"id\"})

  NOTE: The call below will return the status of an already submitted
  Asynchronous CoreAdmin call.
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "REQUESTSTATUS")))


(defn request-recovery
  "The REQUESTRECOVERY action manually asks a core to recover by synching with
  the leader. This should be considered an \"expert\" level command and should
  be used in situations where the node (SorlCloud replica) is unable to become
  active automatically.

  Settings:

  :core <string or keyword> *required*
  The name of the core to re-sync.

  Example:

  (request-recovery client-config {:core \"gettingstarted_shard1_replica1\"})

  The core to specify can be found by expanding the appropriate ZooKeeper node
  via the admin UI.
  "
  [client-config & [{:keys [core] :as settings}]]
  (update! client-config (assoc settings :action "REQUESTRECOVERY")))
