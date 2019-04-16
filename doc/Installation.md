# Solr and Corona Installation

## Prerequisites

### 1. Install Solr 
* download http://archive.apache.org/dist/lucene/solr/8.0.0/solr-8.0.0.tgz (or newest version)
* unzip and move solr-8.0.0 to put solr home folder in you $HOME directory or any place (outside your clojure project)
* go to solr-8.0.0/server/solr and copy solr.xml.
* paste it in your solr home dir (e.g. ~/solr-8.0.0)


### 2. set SOLR_HOME env var to point to solr home dir 

For this library to work properly, you need to set `SOLR_HOME` var pointing to solr home folder you just installed.

For MAC users:
You can put the following in your ~/.bash_profile file 
`source ./.profile`
Then, put the following in your ~/.profile file
```bash
#Solr
export SOLR_HOME="$HOME/solr-8.0.0" # put right version here
export SOLR_ULIMIT_CHECKS=false
```

Notes:
* emacs users: You may need to add `setenv SOLR_HOME /my/path/to/solr/home` to your /etc/launchd.conf file and/or install [exec-path-from-shell](https://github.com/purcell/exec-path-from-shell** package allowing you to read env variables from emacs GUI. 
** [Download](https://github.com/purcell/exec-path-from-shell/tags** the latest release
** install `exec-path-from-shell.el` with `M-x package-install-file`
** insert the following in `~/.emacs.d/init.el`
```
...
;; BEGIN exec-path-from-shell
(use-package exec-path-from-shell
 :ensure t
 :if (memq window-system '(mac ns x))
 :config (setq exec-path-from-shell-variables '("PATH" "SOLR_HOME"))
 (exec-path-from-shell-initialize))
;; END exec-path-from-shell
(require 'server)
(unless (server-running-p) (server-start)))
```
 
### 3. Add corona library in project.clj
[![Clojars Project](https://img.shields.io/clojars/v/corona.svg)](https://clojars.org/corona)

### 4. Add Solr resources to your project

You can either:
* go to the https://github.com/Stylitics/corona-demo repo and copy `resources/solr/tmdb` changing `tmdb` for the name of you core (or index)

* look at examples inside your solr home dir. 

NOTE: If you start from existing example, make sure, in your `$CLJ_PROJECT_HOME/resources/solr/<core-name>/conf/solrconfig.xml` file contains right maching lucene version
`<luceneMatchVersion>8.0.0</luceneMatchVersion>`


### 5. Test solr

#### From command line

* `$ $SOLR_HOME/bin/solr start -p 8983`
* access Solr admin from your browser: http://localhost:8983/solr/
* `$ $SOLR_HOME/bin/solr stop -p 8983`

#### From clojure

For development, you can start solr from Clojure repl. 

* make sure you are seeing `SOLR_HOME` variable. `(System/getenv "SOLR_HOME")`

* then:
```clojure 
(require '[corona.cmd :as cmd])
(cmd/exec! :start) ;; $SOLR_HOME/bin/solr start
```
* access Solr admin from your browser: http://localhost:8983/solr/

* stop with: `(cmd/exec! :stop)`

