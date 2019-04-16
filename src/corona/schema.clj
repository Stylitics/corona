(ns corona.schema
  (:require
   [org.httpkit.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [corona.utils :as utils]))

;; TODO: integrate more fns and helpers from https://lucene.apache.org/solr/guide/7_6/schema-api.html


;; To use  Solr's implicit default behavior if no schemaFactory is explicitly defined. Add the following to your project solrconfig.xml file:
;; <schemaFactory class="ManagedIndexSchemaFactory">
;; <bool name="mutable">true</bool>
;; <str name="managedSchemaResourceName">managed-schema</str>
;; </schemaFactory>
;; Then create your project core/collection:
;; $SOLR_HOME/bin/solr start -e schemaless
;; copy files in $SOLR_HOME/example/schemaless/solr/gettingstarted/conf/
;; paste them in <your-clj-project>/resources/solr/conf/<core-name>
;; inside lang copied folder you can only keep _en file if you want, if so, then remove in "managed-schema" file, xml lines after ""some examples for different languages""
;; from your clojure project run:
;; $SOLR_HOME/bin/solr create -c <core-name> -d resources/solr/conf/<core-name>

;; The Managed Schema is supposed to be manipulated through the follwoing Schema API and not by editing the files present (which include a warning about doing so).

;; The schema.xml file (when not in schemaless mode) is only read once at the first time of startup to create the initial schema, any changes after that has to be done through the Schema API.

;; For data import handler like for atom, db (like sql), mail, solr, and tika:
;; $SOLR_HOME/bin/solr -e dih
;; for more info, see https://lucene.apache.org/solr/guide/6_6/uploading-structured-data-store-data-with-the-data-import-handler.html

(defn make-schema-url
  [client-config & [trailing-uri]]
  (utils/create-client-url client-config (str "/schema" trailing-uri)))


(defn get-fields
  [client-config]
  (let [url (make-schema-url client-config "/fields")
        {:keys [body]} @(http/get url {:as :auto})]
    (json/read-str body :key-fn keyword)))


(defn update-field!
  "Updates fields in schema. Update body has at least one keyword that represents the method and the param as value.
  Available methods:
  :add-field add a new field with parameters you provide.
  :delete-field delete a field.
  :replace-field replace an existing field with one that is differently configured.
  :add-dynamic-field add a new dynamic field rule with parameters you provide.
  :delete-dynamic-field delete a dynamic field rule.
  :replace-dynamic-field replace an existing dynamic field rule with one that is differently configured.
  :add-field-type add a new field type with parameters you provide.
  :delete-field-type delete a field type.
  :replace-field-type replace an existing field type with one that is differently configured.
  :add-copy-field add a new copy field rule.
  :delete-copy-field delete a copy field rule.

  Usage:

  (update-field!
   {:type :http :core :tmdb}
   {:add-field {:name \"id\"
                :type \"pint\"
                :stored true
                :indexed true}})

  (update-field!
   {:type :http :core :tmdb}
   {:delete-field {:name \"id\"}})

  NOTE: only supported with :http config type.
  "
  [client-config body]
  (let [url (make-schema-url client-config)
        options {:body         (json/write-str body)
                 :headers      {"Content-Type" "application/json"}
                 :as           :auto}
        {:keys [body]} @(http/get url options)]
    (json/read-str body :key-fn keyword)))


;;; Update methods sugar

(defn add-field!
  "The add-field command adds a new field definition to your schema. If a field with the same name exists an error is thrown.

  All of the properties available when defining a field with manual schema.xml edits can be passed via the API. These request attributes are described in detail in the section Defining Fields.

  Usage:

  (add-field!
   {:type :http :core :tmdb}
   {:name \"id\"
    :type \"pint\"
    :stored true
    :indexed true})

  ;; add multiple fields at once

  (add-field!
   {:type :http :core :tmdb}
   [{:name \"id\"
     :type \"pint\"
     :stored true
     :indexed true}
    {:name \"pid\"
     :type \"pint\"
     :stored true
     :indexed true}])
  "
  [client-config field-map]
  (update-field! client-config {:add-field field-map}))

;; 
(defn replace-field!
  "Replace a Field
  The replace-field command replaces a field’s definition. Note that you must supply the full definition for a field - this command will not partially modify a field’s definition. If the field does not exist in the schema an error is thrown.

  Usage:
  (replace-field!
   {:type :http :core :tmdb}
   {:name \"id\"
    :type \"string\"
    :stored true
    :indexed true})
  "
  [client-config field-map]
  (update-field! client-config {:replace-field field-map}))

(defn delete-field!
  "Delete a Field
  The delete-field command removes a field definition from your schema. If the field does not exist in the schema, or if the field is the source or destination of a copy field rule, an error is thrown.

  Usage:

  (delete-field!
   {:type :http :core :tmdb}
   {:name \"id\"})
  "
  [client-config field-map]
  (update-field! client-config {:delete-field field-map}))


(defn add-field-type!
  "Add a New Field Type
  The add-field-type command adds a new field type to your schema.

  All of the field type properties available when editing schema.xml by hand are available for use in a POST request. The structure of the command is a json mapping of the standard field type definition, including the name, class, index and query analyzer definitions, etc. Details of all of the available options are described in the section Solr Field Types.

  Usage:
  "
  [client-config field-map]
  (update-field! client-config {:add-field-type field-map}))


(defn delete-field-type!
  "Delete a Field
  The delete-field command removes a field definition from your schema. If the field does not exist in the schema, or if the field is the source or destination of a copy field rule, an error is thrown.

  Usage:

  (delete-field-type!
   {:type :http :core :tmdb}
   {:name \"id\"})
  "
  [client-config field-map]
  (update-field! client-config {:delete-field-type field-map}))

(defn get-field-types
  [client-config]
  (let [url (make-schema-url client-config "/fieldtypes")
        {:keys [body]} @(http/get url {:as :auto})]
    (json/read-str body :key-fn keyword)))
