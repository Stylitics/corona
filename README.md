# corona
Clojure Solr wrapper (pre-alpha stage, expect API to break)

## Why?

### Why use Solr?

* Search is a central part of your product 
* Relevancy is the first reason why you need search

### Why use corona?

* You want to use Solr 7 (soon Solr 8) from the joy of Clojure

## Companies

### Who is using Solr?

* Netflix
* Instagram 
* DuckDuckGO 
* Bloomberg
* and many more...

### Who is using corona?

* Stylitics


## Documentation

* doc/Installation.md
* doc/Data-Import.md

## Development 

### TEMP: Building private repo
Source your env vars and run `lein deploy private1`

### ROADMAP:

* bring cljs compatibility by replacing clj-http to cljs-ajax (or similar) as it exposes the same interface (where useful) in both Clojure and ClojureScript.

## License

This library is available to use under MIT license.
