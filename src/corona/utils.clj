(ns corona.utils
  (:require [clojure.data.json :as json]))

(def default-http-config
  "Needs a custom :core value"
  {:host "127.0.0.1" ;"localhost"
   :port 8983
   :path "/solr"
   ;;:core ""
   })

(def ^{:dynamic true
       :doc "If set to true, (json-read-str) will throw exception, instead of
returning nil when parsing JSON fails."}
  *json-read-throw-on-error* false)

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

(defn json-read-str
  "Try to parse JSON string. Returns nil if string is empty or unable to parse.
Default :key-fn is \"keyword\". If *json-read-throw-on-error* was set to true,
throw exception on any error."
  ([s key-fn]
     (try
       (json/read-str s :key-fn key-fn)
       (catch Exception e
         (when *json-read-throw-on-error*
           (throw e)))))
  ([str] (json-read-str str keyword)))
