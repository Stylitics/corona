(ns corona.query
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [corona.utils :as utils]
   [org.httpkit.client :as http]))


;;; Params

(defn format-param
  [p]
  (cond
    (and (sequential? p) (number? (last p))) (string/join "^" p)
    (keyword? p) (name p)
    :else (str p)))

(defn format-values
  [v]
  (mapv format-param (if (coll? v) v [v])))

(defn format-params
  [m]
  (reduce-kv
   (fn [m k v]
     (let [k* (format-param k)]
       (assoc m k* (string/join (if (#{"sort" "fl"} k*) "," " ")
                                (format-values v)))))
   {}
   m))


;;; Settings

(def default-term-vectors-settings
  {:tv true
   :tv.df true
   :tv.tf true
   ;;:tv.tf_idf true ;(* (Math/pow tf 0.5) (/ 1 df))
   :start 0
   :rows 1
   ;;:indent true
   })

(def mlt-keys [:mlt.fl
               :mlt.mintf
               :mlt.mindf
               :mlt.maxdf
               :mlt.maxdfpct
               :mlt.minwl
               :mlt.maxwl
               :mlt.maxqt
               :mlt.maxntp
               :mlt.boost
               :mlt.qf
               :mlt.match.include
               :mlt.match.offset
               :mlt.interestingTerms])

(defn build-internal-mlt-settings
  "NOTE: :fl is not considered to get back all keys of matching doc."
  [settings]
  (merge (select-keys settings mlt-keys)
         {:q (:mlt.q settings)
          :mlt.interestingTerms "details"
          :fq (:fq settings)}))


;;; Terms

(defn mlt-resp->terms
  [mlt-resp]
  (->> mlt-resp
       :interestingTerms
       (partition 2)
       (mapv (fn [[field+term score]]
               (let [[field term] (string/split field+term #"\:")]
                 [field term score])))))

(defn vectorize-qf-element
  [qf-element]
  (if (string? qf-element)
    (-> qf-element
        (string/split #"\^")
        ((fn [[qf-field qf-boost-str]]
           [qf-field (Float/parseFloat qf-boost-str)])))
    qf-element))

(defn mlt-resp->raw-terms
  [mlt-resp mlt-qf-raw]
  (let [matched (->> mlt-resp :match :docs first)]
    (reduce (fn [mlt-qf-raw match-el]
              [mlt-qf-raw match-el])
            mlt-qf-raw
            matched)))

(defn boost-terms
  [mlt-terms mlt-qf & [mlt-boost-factor]]
  (reduce
   (fn [sim-terms mlt-qf-element]
     (let [[mlt-qf-field mlt-qf-boost] (vectorize-qf-element mlt-qf-element)]
       (reduce
        (fn [acc [field term score :as v]]
          (conj
           acc
           [field term (cond-> score
                         (= mlt-qf-field field) (* mlt-qf-boost
                                                   (or mlt-boost-factor 1)))]))
        []
        sim-terms)))
   mlt-terms
   mlt-qf ;;FIXME: when not there, qf boost one need also mlt.fl ?
   ))

(defn term-vectors-resp->interesting-terms-per-field
  "Digests the response from tvrh handler call targetting matching doc id."
  [tv-resp & [qf top-k]]
  (let [[id [_ _ & rst]] (:termVectors tv-resp)
        qf-map (into {} qf)]
    (for [[field tokens] (partition 2 rst)]
      [field (take
              (or top-k 8)
              (sort-by second >
                       (for [[token stats] (partition 2 tokens)
                             :let [[_ tf _ df] stats
                                   tf-idf (* (Math/pow tf 0.5) (/ 1 df))]]
                         [token (+ (+ 1 (* 100 tf-idf))
                                   (or (get qf-map field) 0))])))])))

(defn terms-per-field->q
  [terms-map]
  (->>  terms-map
        (mapcat (fn [[field terms]]
               (map (fn [[term score]] (str field ":" term "^" (or score 1)))
                       terms)))
        (string/join " ")))

(defn tv-terms->q
  [tv-terms & [q]]
  (let [tv-terms-str (terms-per-field->q tv-terms)]
    (cond->> (format "(%s)" tv-terms-str)
      (seq q) (str q " "))))

(defn terms->q
  ""
  [terms]
  (->> terms
       (map (fn [[field term score]]
              (str field ":" term "^" (or score 1))))
       (string/join " " )))

(defn mlt-terms->q
  [mlt-q mlt-terms & [q]]
  (let [mlt-terms-str (terms->q mlt-terms)]
    (format "-%s %s %s" mlt-q mlt-terms-str q)))



;;; Query API

;; SOURCE: https://lucene.apache.org/solr/guide/7_7/query-syntax-and-parsing.html#query-syntax-and-parsing

(defn query-handler
  [client-config handler settings]
  (let [handler-uri (str "/" (name handler))
        query-params (format-params settings)
        options {:query-params query-params
                 :as :auto}
        url (utils/create-client-url client-config handler-uri)
        {:keys [body]} @(http/get url options)]
    (json/read-str body :key-fn keyword)))

(defn query-term-vectors
  [client-config settings]
  (query-handler
   client-config
   :tvrh
   (merge default-term-vectors-settings settings)))

(defn query
  "Makes and executes solr query from setting map
  Uses solr /select route.
  Returns decoded response of solr service.
  "
  [client-config settings]
  (query-handler
   client-config
   :select
   settings))


(defn query-mlt
  "A MoreLikeThis query that uses MLT request handler (/mlt route) to give back
  similar results to a matching document identified in the query under :q
  (e.g. {:q id:12345}.)

  From the specified document, MLT handler will build a query behind the scenes,
  by searching for 'interesting terms' from fields specified under :fl key.

  PriorityQueue is used to fetch the scores for all the terms, which are then
  added as boost queries to a large set of terms in a boolean query, where each
  term is set to SHOULD occur. That way the terms are boosted based on MLT
  semantics, while it uses the ClassicSimilarity behind the scenes.

  These values will be used to build the boost term queries:
  tq = new BoostQuery(tq, boostFactor * myScore / bestScore); 
   e.g. Queue = Term1:100 , Term2:50, Term3:20, Term4:10 
   => Term1:10 , Term2:5, Term3:2, Term4:1 

  settings map:

  :q <string> default: \"*:*\" (everything)
  Query terms

  :fq
  Filter query, this does not affect the search, only what gets returned

  :mlt.fl <string>, default: \"contents\"
  The fields to use for similarity. 
  NOTE: if possible use stored TermVectors in the managedschema file for fields
  (e.g. <field name=\"cat\" ... termVectors=\"true\" />)
  If termVectors are not stored, MoreLikeThis will generate terms from stored fields.

  :mlt.mintf <int>, default: 2
  Minimum Term Frequency - the frequency below which terms will be
  ignored in the source doc. 
  NOTE: Getting good MLT results require some fine-tuning based on experimentation,
  in particular mlt.mintf. Start low and slowly increase until you start getting
  results that \"feel right\".

  :mlt.mindf <int>, default: 5
  Minimum Document Frequency - the frequency at which words will be
  ignored which do not occur in at least this many docs.

  :mlt.minwl <int>, default: 0
  Minimum word length below which words will be ignored.

  :mlt.maxwl <int>, default: 0
  Maximum word length above which words will be ignored.

  :mlt.maxqt <int>, default: 25
  Maximum number of query terms that will be included in any generated query.

  :mlt.maxntp <int>, default: 5000
  Maximum number of tokens to parse in each example doc field that is not stored
  with TermVector support.

  :mlt.boost <bool>, default: false
  [true/false] set if the query will be boosted by the interesting term relevance.
  
  :mlt.qf
  Query fields and their boosts using the same format as that used in
  DisMaxQParserPlugin. These fields must also be specified in mlt.fl.

  :mlt.match.include <bool>, default: true
  Specifies whether or not the response should include the matched document
  under :match key.

  :mlt.match.offset
  Specifies an offset into the main query search results to locate the document
  on which the MoreLikeThis query should operate. By default, the query operates
  on the first result for the q parameter.

  :mlt.interestingTerms <[\"list\", \"none\", \"details\"]>
  Controls how the MoreLikeThis component presents the \"interesting\" terms
  (the top TF/IDF terms) for the query. Supports three values.
  - \"list\" : lists the terms.
  - \"none\" : lists no terms.
  - \"details\": lists the terms along with the boost value used for each term.
  Unless mlt.boost=true, all terms will have boost=1.0.

  :fl
  Fields to return. We force 'id' to be returned so that there is a unique
  identifier with each record.

  :wt <enum>, default: \"json\"
  Data type returned.

  :start <int>, default: 0
  Record to start at

  :rows <int>, default: 10
  Number of records to return.
  "
  [client-config settings]
  (query-handler
   client-config
   :mlt
   settings))

(defn query-mlt-tv-edismax
  "Like more like this handler query or `query-mlt` but

  - takes top-k terms *PER FIELD*, for more explanations, see
    https://github.com/DiceTechJobs/RelevancyFeedback#isnt-this-just-the-mlt-handler

  - allows edismax params (e.g. `:boost` `:bf` `:bq` `:qf`)
    NOTE: To better understand boosting methods, see
    https://nolanlawson.com/2012/06/02/comparing-boost-methods-in-solr/

  Special settings:

  :mlt.q <string>
  To reach the matching document to get interesting terms.

  Supported mlt keys: :mlt-fl, :mlt-qf

  IMPORTANT: All mlt.fl fields MUST be set as TermVectors=true in the managedschema
  for the mlt query to be integrated to main q.
  "
  [client-config settings]
  (let [mlt-q (:mlt.q settings)
        tv-resp (query-term-vectors
                 client-config
                 {:q mlt-q
                  :fl (:mlt.fl settings)})
        tv-terms (term-vectors-resp->interesting-terms-per-field
                  tv-resp
                  (:mlt.qf settings))
        q (tv-terms->q tv-terms (:q settings))
        fq (string/join " " [(:fq settings) (format "-(%s)" mlt-q)])
        settings (-> settings
                     (assoc :q q)
                     (assoc :fq fq)
                     (dissoc mlt-keys)
                     (dissoc :mlt.q))
        resp (query client-config (merge {:defType "edismax"} settings))]
    (assoc resp :interestingTerms tv-terms :match (-> tv-resp :response))))
