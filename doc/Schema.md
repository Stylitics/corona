# Schema

`corona.schema` allows you to manage many of the elements of your schema and change from clojure your `<core>/conf/managed-schema` file. 

### Example Usage:

```clojure
(def conn {:type :http :core :tmdb})

(add-field-type!
   conn
   {:name "myNewTxtField"
    :class "solr.TextField"
    :positionIncrementGap "100"
    :analyzer {:charFilters [{:class "solr.PatternReplaceCharFilterFactory"
                              :replacement "$1$1"
                              :pattern "([a-zA-Z])\\\\1+"}],
               :tokenizer {:class "solr.WhitespaceTokenizerFactory"}
               :filters [{:class "solr.WordDelimiterFilterFactory"
                          :preserveOriginal "0"}]}})
  (get-field-types conn)
  (delete-field-type! conn {:name "myNewTxtField"})

  (add-field! conn {:name "id"
                    :type "pint"
                    :stored true
                    :indexed true})
  (get-fields conn)
  ;;already exist but ytpe is string, let's change type:
  (replace-field! conn {:name "id",
                        :type "pint",
                        :multiValued false,
                        :indexed true,
                        :required true,
                        :stored true})
  (get-fields conn)
  (delete-field! conn {:name "id"}))

```
  

  
