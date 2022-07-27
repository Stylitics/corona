(defproject corona "0.1.15"
  :description "A clojure wrapper Solr client"
  :url "https://github.com/Stylitics/corona"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [commons-io/commons-io "2.6"]
                 [funcool/cuerdas "2.0.5"]
                 [http-kit "2.4.0"]
                 [me.raynes/fs "1.4.6"]
                 [metosin/jsonista "0.3.3"]
                 [ring/ring-codec "1.1.1"]]
  :source-paths ["src"]
  :profiles {:uberjar {:aot :all}}
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]]
  :main corona.core-admin)
