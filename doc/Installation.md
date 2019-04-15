# Solr and corona Installation

## Perequeties

### 1. Install solr 
Follow the instructions here: https://lucene.apache.org/solr/guide/7_6/installing-solr.html

Put solr home folder outside your project. A good place could be in your User home directory there: (System/getProperty "user.home")

### 2. Set `SOLR_HOME` var
For this library to work properly, you need to set `SOLR_HOME` var pointing to solr home folder you just installed.

For MAC users:
You can put the following in your ~/.bash_profile file 
`source ./.profile`
Then, put the following in your ~/.profile file
```bash
#Solr
export SOLR_HOME="$HOME/solr-7.6.0" # put right version here
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

Here is what worked for me 
### 3. Add corona library in project.clj
[![Clojars Project](https://img.shields.io/clojars/v/corona.svg)](https://clojars.org/corona)

### 4. Test solr
From clojure repl, make sure you are seeing `SOLR_HOME` variable. 

```clojure
(System/getenv "SOLR_HOME")
```
Then:
```clojure 
(require '[corona.cmd :as cmd])
(cmd/exec! :start) ;; same as terminal command: $SOLR_HOME/bin/solr start
```
You can now access Solr admin from your browser: http://localhost:8983/solr/

To stop:
```clojure
(cmd/exec! :stop)
```

