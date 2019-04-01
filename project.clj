(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))
(defproject corona "0.1.0-SNAPSHOT"
  :description "A clojure wrapper Solr client"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.apache.solr/solr-solrj "7.6.0"]
                 [org.apache.lucene/lucene-core "7.6.0"]
                 [org.apache.solr/solr-core "7.6.0"]
                 [org.apache.solr/solr-dataimporthandler "7.6.0"]
                 [org.apache.solr/solr-ltr "7.6.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]
                 [commons-io/commons-io "2.6"]
                 [clj-http "3.9.1"]
                 [me.raynes/fs "1.4.6"]
                 [ring/ring-codec "1.1.1"]]
  :plugins [[s3-wagon-private "1.3.1"]]
  :repositories [["private1" {:url "s3p://stylitics.clojure/corona/releases/"
                              :no-auth true}]]
  :source-paths ["src"]
  :java-source-paths ["jsrc"]
  :main corona.client
)
