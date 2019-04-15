(ns corona.utils)


(def default-http-config
  "Needs a custom :core value"
  {:host "127.0.0.1" ;"localhost"
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




