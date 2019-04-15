(defproject corona "0.1.0-SNAPSHOT"
  :description "A clojure wrapper Solr client"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]
                 [commons-io/commons-io "2.6"]
                 [clj-http "3.9.1"]
                 [me.raynes/fs "1.4.6"]
                 [ring/ring-codec "1.1.1"]]
  :source-paths ["src"]
  :profiles {:uberjar {:aot :all}}
  :main corona.client
)
