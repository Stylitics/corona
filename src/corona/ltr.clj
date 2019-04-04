(ns corona.ltr
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [corona.client :as client]))

;;; NOTE: WIP - Vastly incomplete and sometimes too specific. Please submit PR.


;;; Feature extraction helpers

(defn read-distinct-coll
  "Returns all distinct values across records under a given multiValued field."
  [records field]
  (->> records (mapcat field) (map string/lower-case) (distinct) (doall)))

(defn gen-coll-features
  "Builds multivalued field features, one per distinct field value across
  all records
  eg. for field 'genres': 'hasGenresAction', 'hasGenresDrama', etc."
  [records field store-name & [params]]
  (->> (read-distinct-coll records field)
       (map (fn [g] {:store  store-name
                     :name   (str "has"
                                  (string/capitalize (name field))
                                  (string/capitalize (string/replace g #" " "_")))
                     :class  "org.apache.solr.ltr.feature.SolrFeature"
                     :params (merge {:q (str "{!func}termfreq(" (name field) ",'" g "')")}
                                    params)}))))

(defn gen-field-feature
  "Builds feature description for simple records 'field'
  'store-name' - string name of store to specify for features."
  [field store-name & [params]]
  {:store  store-name
   :name   (name field)
   :class  "org.apache.solr.ltr.feature.FieldValueFeature"
   :params (merge {:field (name field)} params)})

(defn gen-external-value-feature
  "Builds feature description for simple externally supplied  value with key 'value-k'.
  'required?' - (true/false) if value will'be required with ltr query;
  'store-name' - string name of store to specify for features."
  [value-k required? store-name & [params]]
  {:pre [(or (true? required?) (false? required?))]}
  {:store  store-name
   :name   (name value-k)
   :class  "org.apache.solr.ltr.feature.ValueFeature"
   :params (merge {:value    (str "${" (name value-k) "}")
                   :required (-> required? false? not)}
                  params)})


;;; Feature Store

(defn make-feature-store-url
  [client-config & [uri]]
  (client/create-client-url
   client-config
   (str "/schema/feature-store" (when uri (str "/" uri)))))

(defn delete-feature-store!
  "Deletes all features in store 'store-name' (or _DEFAULT_)
   executing HTTP DELETE request to feature-store-url built from client-config
   Returns json-decoded body of response."
  [client-config & [store-name]]
  (-> (make-feature-store-url client-config (or store-name "_DEFAULT_"))
      (http/delete {:throw-exceptions false
                    :accept           :json})
      :body
      (json/read-str :key-fn keyword)))

(defn upload-features!
  "Uploads vector of feature descriptions 'features' to solr.
  executing HTTP PUT request to feature-store-url built from client-config
  WARN: clears store before uploading.
  Returns json-decoded body of response."
  [client-config features]
  (delete-feature-store! client-config (-> features first :store))
  (-> (make-feature-store-url client-config)
      (http/put {:throw-exceptions false
                 :body             (json/write-str features)
                 :content-type     :json
                 :accept           :json})
      :body
      (json/read-str :key-fn keyword)))


;;; Model

(defn do-to-last-n
  "Maps 'entries' with identity while index < 'n'
  and starting from n maps with 'f', which takes following args:
  index - index of entry assuming 0 is n at original 'entries' collection
  entry - entry to map.
  Returns mapped collection."
  [entries n f]
  (let [start (- (count entries) n)]
    (map-indexed (fn [index entry]
                   (if (< index start) entry (f (- index start) entry)))
                 entries)))

(defn prepare-features-for-nn
  [store-name built-features mins maxs]
  (-> built-features
      (do-to-last-n
       (count mins)
       (fn [index feat]
         (assoc feat :norm
                {:class  "org.apache.solr.ltr.norm.MinMaxNormalizer"
                 :params {:min (str (get mins index))
                          :max (str (get maxs index))}})))
      vec))

(defn build-solr-ltr-nn-model
  "Builds ready-to-json structure for solr NeuralNetworkModel.
  Returns map."
  [store-name model-name built-features layers mins maxs]
  {:store store-name
   :name  model-name
   :class    "org.apache.solr.ltr.model.NeuralNetworkModel"
   :features (prepare-features-for-nn store-name built-features mins maxs)
   :params   {:layers layers}})


;;; Update model store

(defn make-model-store-base-url
  [client-config]
  (client/create-client-url client-config "/schema/model-store"))

(defn delete-model!
  "Deletes EXISTING ltr model with name 'model-name' from solr
  executing HTTP DELETE request to 'sorl-core-url' or http://localhost:8983/solr/tmdb
  WARN: solr can hang on attempt on deleting nonexistent model.
  Returns json-decoded body of response."
  [client-config model-name]
  (->
   (http/delete (str (make-model-store-base-url client-config) "/" model-name)
                 {:throw-exceptions false
                  :accept           :json})
    :body
    (json/read-str :key-fn keyword)))

(defn upload-model!
  "Uploads 'model' as ltr model to solr, json-encoding it previously,
  executing HTTP PUT request to 'sorl-core-url'
  Returns json-decoded body of response.
  NOTE: only supported with :http config type."
  [client-config model]
  (-> (make-model-store-base-url client-config)
      (http/put {:throw-exceptions false
                 :body             (json/write-str model)
                 :content-type     :json
                 :accept           :json})
      :body
      (json/read-str :key-fn keyword)))
