(ns corona.cmd
  (:require
   [clojure.java.shell :refer [sh]]
   [me.raynes.fs :as fs]))

(def ^:dynamic *solr-home* (fs/expand-home (System/getenv "SOLR_HOME")))

;;FIXME: add windows variant: bin/solr.cmd
(def bin-solr (str *solr-home* "/bin/solr"))

(defn exec! [& commands]
  (:out (apply sh bin-solr (map name commands))))

(defn delete-core!
  [core & commands]
  (let [cmds (into [ "-c" (name core)] commands)]
    (apply exec! :delete cmds)))

(defn create-core!
  [core project-core-conf-dir & commands]
  (let [cmds (into ["-c" (name core) "-d" project-core-conf-dir]
                   commands)]
    (apply exec! :create cmds)))

(defn copy-core-template!
  [example-key core-path]
  (case example-key
    :dih/atom   (fs/copy-dir (str *solr-home* "/example/example-DIH/solr/atom") core-path)
    :dih/db     (fs/copy-dir (str *solr-home* "/example/example-DIH/solr/db")   core-path)
    :dih/mail   (fs/copy-dir (str *solr-home* "/example/example-DIH/solr/mail") core-path)
    :dih/solr   (fs/copy-dir (str *solr-home* "/example/example-DIH/solr/solr") core-path)
    :dih/tika   (fs/copy-dir (str *solr-home* "/example/example-DIH/solr/tika") core-path)
    :files      (fs/copy-dir (str *solr-home* "/example/files") core-path)
    :films      (fs/copy-dir (str *solr-home* "/example/films") core-path)
    :schemaless (fs/copy-dir (str *solr-home* "/example/schemaless/solr/gettingstarted") core-path)))
