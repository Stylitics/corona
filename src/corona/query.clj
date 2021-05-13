(ns corona.query
  (:require
   [jsonista.core :as json]
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
       (assoc m k* (string/join (if (#{"sort" "fl" "tv.fl" "mlt.fl"} k*)
                                  ","
                                  " ")
                                (format-values v)))))
   {}
   m))


;;; Settings

(def default-term-vectors-settings
  {:tv true
   :tv.df true
   :tv.tf true
   :tv.tf_idf true ;(* (Math/pow tf 0.5) (/ 1 df))
   :start 0
   :rows 1})

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

;;; Terms

(defn mlt-ids->tv-q
  [mlt-ids & [mlt-field-name]]
  (let [ids (if (sequential? (first mlt-ids))
              (map first mlt-ids)
              mlt-ids)]
    (->> ids
         (map #(str mlt-field-name ":" %))
         (string/join " "))))

(defn partition-kvs
  [[k v & rst]]
  (into [[k (if (sequential? v) (partition-kvs v) v)]]
        (when (seq rst) (partition-kvs rst))))

(defn tf-idf
  [tf df]
  (* (Math/pow tf 0.5) (/ 1 df)))

(defn qualified-term?
  [term tf df mintf mindf minwl]
  (cond
    (< tf mintf) false
    (< df mindf) false
    (< (count term) mindf) false
    :else true))

(defn term-vectors-resp->interesting-terms-per-field
  "Digests the response from tvrh handler and creates a interestingTerms map
  per matching document using mlt special keys."
  [tv-resp & [{qf :mlt.qf ids :mlt.ids top :mlt.top boost :mlt.boost
               mintf :mlt.mintf mindf :mlt.mindf minwl :mlt.minwl
               :or {top 15
                    mintf 1
                    mindf 3
                    minwl 3}}]]
  (let [term-vectors (dissoc (into {} (partition-kvs (:termVectors tv-resp)))
                             "warnings")
        qf-map  (into {} qf)
        ids-map (into {} ids)]
    (into
     {}
     (mapv
     (fn [[id fields]]
       [id (into
            {}
            (mapv
             (fn [[field terms]]
               (let [scored-terms (map
                                   (fn [[term stats]]
                                     (let [{:strs [tf df tf-idf payload]} (into {} stats)]
                                       (when (qualified-term? term tf df mintf mindf minwl)
                                         (let [weight (cond
                                                        payload payload
                                                        boost tf-idf
                                                        :else 1)]
                                           [term weight tf df]))))
                                   terms)
                     sorted-terms (sort-by second > (remove nil? scored-terms))
                     top-terms (take top sorted-terms)
                     top-terms-count (count top-terms)
                     top-terms-total-score (reduce (fn [acc term]
                                                     (+ acc (second term)))
                                                   0
                                                   top-terms)
                     normalized-top-terms (mapv
                                           (fn [[term score tf df]]
                                             (let [norm-score (/ score top-terms-total-score)]
                                               [term
                                                (* norm-score
                                                   (or (get qf-map field) 1)
                                                   (or (get ids-map id) 1))
                                                tf
                                                df]))
                                           top-terms)]
                 [field normalized-top-terms]))
             (rest fields) ;removes the ["uniqueKey" "206647"] first kv
             ))])
     term-vectors))))

(defn terms-per-field->q
  [terms-map]
  (->>  terms-map
        (mapcat (fn [[field terms]]
                  (map (fn [[term score]] (str field ":\"" term "\"^" (or score 1)))
                       terms)))
        (string/join " ")))

(defn interesting-terms-per-field->q
  [interesting-terms-per-field settings]
  (->> interesting-terms-per-field
       vals
       (map terms-per-field->q)
       (string/join " ")))

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
        url (utils/create-client-url client-config handler-uri)]
    (-> @(http/get url options) :body utils/json-read-str)))

(defn query-term-vectors
  "Settings

  :tv <bool>, default: false
  If true, the Term Vector Component will run.

  :tv.docIds <sequential>
  For a list of Lucene document IDs (not the Solr Unique
  Key), term vectors will be returned.

  :tv.fl <vector>
  For a given list of fields, term vectors will be returned.
  If not specified, the fl parameter is used.

  :tv.all <bool>, default: false
  If true, all the boolean parameters listed below (tv.df, tv.offsets,
  tv.positions, tv.payloads, tv.tf and tv.tf_idf) will be enabled.

  :tv.df <bool>, default: false
  If true, returns the Document Frequency (DF) of the term in the collection.
  This can be computationally expensive.

  :tv.offsets <bool>, default: false
  If true, returns offset information for each term in the document.

  :tv.positions <bool>, default: false
  If true, returns position information.

  :tv.payloads <bool>, default: false
  If true, returns payload information.

  :tv.tf <bool>, default: false
  If true, returns document term frequency info for each term in the document.

  :tv.tf_idf <bool>, default: false
  If true, calculates TF / DF (i.e.,: TF * IDF) for each term. Please note that
  this is a literal calculation of \"Term Frequency multiplied by Inverse
  Document Frequency\" and not a classical TF-IDF similarity measure.
  This parameter requires both tv.tf and tv.df to be \"true\". This can be
  computationally expensive. (The results are not shown in example output)
  "
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

  :mlt.field <string>, default: \"id\"
  The name of the id field

  :mlt.ids
  A lest of ids and boosts e.g. [[\"12345\" 3] [\"12346\" 2]]

  :mlt.top <int> 
  The number of top interesting terms to use, per field.

  :q
  \"Regular edismax query\" that is added to mlt query

  Special vars:

  ${mltq}
  This is the computed interesting-term query you can pass in.
  e.g. {!boost b=recip(ms(NOW,date),3.16e-11,1,1)^100 v=\"{!lucene v='(${mltq})'}\"}

  Supported mlt keys:
  :mlt.fl
  :mlt.mintf
  :mlt.mindf
  :mlt.minwl
  :mlt.boost
  :mlt.qf

  IMPORTANT: All mlt.fl fields MUST be set as TermVectors=true in the managedschema
  for the mlt query to be integrated to main q.
  "
  [client-config settings]
  (let [tv-q (mlt-ids->tv-q (:mlt.ids settings) (or (:mlt.field settings) "id"))
        tv-resp (query-term-vectors
                 client-config
                 {:q tv-q
                  :tv.fl (:mlt.fl settings)
                  :tv.all true
                  :rows (count (:mlt.ids settings))})
        int-terms (term-vectors-resp->interesting-terms-per-field
                   tv-resp
                   settings)
        mltq (interesting-terms-per-field->q int-terms settings)
        fq (string/join " " [(:fq settings) (format "-(%s)" tv-q)])
        settings (-> settings
                     (assoc :mltq mltq)
                     (assoc :fq fq)
                     (dissoc mlt-keys)
                     (dissoc :mlt.field :mlt.qf :mlt.ids :mlt.top))
        resp (query client-config (merge {:defType "edismax"} settings))]
    (assoc resp :interestingTerms int-terms :match (-> tv-resp :response))))
