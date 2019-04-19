# Importing/Indexing database (PostgreSQL) in Solr using corona.data-import namespace

## Data Importer Setup Instructions

### 1. Download the JDBC driver 
Download the JDBC driver for PostgreSQL here:
https://jdbc.postgresql.org/download.html

Copy file `postgresql-*.jar` from the downloaded archive and it in `$SOLR_HOME/contrib/dataimporthandler/lib`

Create `lib` folder if needed.

### 2. Start from dih db collection example

```clojure
(cmd/exec! :start) 

(cmd/copy-core-template!
  :dih/db
  "$YOUR_CLOJURE_PROJECT/resources/solr/<my-core-name>")

(cmd/exec! :stop)
```
### 3. Edit solr-config.xml file

In resources/solr/<my-core-name>/conf/solr-config.xml change requestHandler named dataimport to:
```xml
<requestHandler name="/dataimport" class="org.apache.solr.handler.dataimport.DataImportHandler">
  <lst name="defaults">
    <str name="config">resources/solr//db-data-config.xml</str>
  </lst>
</requestHandler>
``` 
Add following tags under the `<config>` tag so that Solr picks up the Custom JARs
```xml
<lib dir="${solr.install.dir:../../../..}/contrib/dataimporthandler/lib" regex=".*\.jar" />
<lib dir="${solr.install.dir:../../../..}/dist/" regex="solr-dataimporthandler-.*\.jar" />
```

### 4. Edit db-data-config.xml file

The file `db-data-config.xml` is located in same folder as `solrconfig.xml` and defines data we want to import/index from our datasource.

Change datasource tag
```xml
<dataSource 
  driver="org.postgresql.Driver" 
  url="jdbc:postgresql://localhost:5432/my-db-name
  user="sa" 
  password="secret"/> 
```

Change document tag:
```xml
<document>
  <entity 
    name="product"
    pk="id"
    query="select id,name from products"
    deltaQuery="SELECT id FROM products WHERE updated_at &gt; ${dataimporter.last_index_time}'::timestamp at time zone 'utc'">
    deltaImportQuery="SELECT id,name from products WHERE id='${dataimporter.delta.id}'"
  <field column="id" name="id"/>
  <field column="name" name="name"/>
  </entity>
</document>
```
Assuming that our DB named `my-db-name` and we have table `products` with columns `id`, `name` and `updated_at`.

Column 'updated_at' of datetime type stores the date of last modification of the row.
This column will be used in incremental import to track rows modified since the last import into Solr.

* The `pk` attribute signals the primary key of db entity.
* The `query` attribute gives the data needed to populate fields of the Solr document in full-import.
* The `deltaQuery` attribute gives the primary keys of the current entity which have changes since the last index time.
* The `deltaImportQuery` attribute gives the data needed to populate fields when running a delta-import.

Full-import command uses the `query` query, delta-import command uses the `delta...` components.

Notes:
* Timezones: One issue that i had was with difference in timezone of SQL and Solr due to which even my delta-imports were functioning as full-imports.
* dataimporter vars: `dataimporter.last_index_time var` is automatically stored in `$SOLR_HOME/my-core-name/conf/dataimport.properties`. 	
* deltaImportQuery: Even if you don't need primary key, you need to select it:
This breaks: SELECT column_1, column_2, column_3 FROM table WHERE id = ${dataimporter.delta.id} 
This works: SELECT id, column_1, column_2, column_3 FROM table WHERE id = ${dataimporter.delta.id} 
Also note the names are case sensitive. if the id comes as 'ID' this will not work.
* deletedPkQuery: If you had that you could add `deletedPkQuery` attribute in your <entity> as follows:
```
deletedPkQuery="SELECT id FROM deleted_item WHERE deleted_at > '${dataimporter.last_index_time}'"
```
* deltaQuery: You may need to escape `>` symbole with `&gt;`.

Alternatively, you can also do delta imports solely via `query`. 
https://wiki.apache.org/solr/DataImportHandlerDeltaQueryViaFullImport

### 5. Cleanup and edit managed-schema file

```xml
    <schema name="example" version="1.5">
        <field name="_version_" type="long" indexed="true" stored="true"/>
        <field name="id" type="string" indexed="true" stored="true" required="true" multiValued="false" /> 
        <field name="name" type="string" indexed="true" stored="true" multiValued="false" />
```

Note: 
Clean up conf files: the your project `resources/solr/conf` folder can have less files. You may want to remove all dictionary type files. Though you also need to remove the references to it in managed-schema file.
corona.schema: You can use corona.schema to dynamically add fields to schema.
  
### 6. Perform full or delta import 

```clojure

(require [corona.client :as client]
         [corona.data-import :as data-import])

(def client-config
  {:type :http
   :host "127.0.0.1"
   :port 8983
   :path "/solr"
   :core "my-core-name"})

(def client (client/create-client client-config))

(defn import-reset!
  []
  (cmd/exec! :stop)
  
  (println "SOLR: Starting Solr...")
  (cmd/exec! :start)
  
  (println "SOLR: Deleting :my-core-name core...")
  (cmd/delete-core! :my-core-name)
  
  (println "SOLR: Creating back :my-core-name core...")
  (cmd/create-core! :my-core-name "resources/solr/my-core-name/conf")
  (println "SOLR: Reloading import conf...")
  (solr.data-import/reload-config! client-config)
  
  (println "SOLR: Ready to import documents :-)"))

(import-reset!)

```
You can now perform *full-import*

```clojure
(data-import/full-import! client-config {:clean true})

;; Run several times until completed:
(data-import/status client-config)

```

The full import loads all data every time, while incremental import means only adding the data that changed since the last indexing. 

By default, full import starts with removal the existing index (parameter clean=true).

Then, add an entrie in your table and can perform *delta-import*

```clojure
(data-import/delta-import! client-config {:clean false :commit true})
(data-import/status client-config)

```
Then, lastly copy the last id of the last entry you added and run a solr query!
```
(client/query client {:q "id:12345" :fl ["id"]}) 
;; change 12345 to your last id
```

Notes:
* delta-import: Use clean=false while running delta-import command.
debug=true - The debug mode limits the number of rows to 10 by default and it also forces indexing to be synchronous with the request.

* Admin console: After successfully adding a collection (or core) to Solr you also can select it and run dataimport commands from Solr Admin. 
** full-import: http://imgur.com/a/raVE4
** delta-import: http://imgur.com/a/raVE4

* Error logs: You can find error and info logs in $SOLR_HOME/server/logs/solr.log

## DEV
### TODO:
Use SolrJ for less XML. THis can be a starting point:
https://wiki.apache.org/solr/SolJava

## References

* http://wiki.apache.org/solr/DIHQuickStart
* http://wiki.apache.org/solr/DataImportHandler
* https://lucene.apache.org/solr/guide/7_6/uploading-structured-data-store-data-with-the-data-import-handler.html#uploading-structured-data-store-data-with-the-data-import-handler
* https://gist.github.com/rnjailamba/8c872768b136a88a10b1
* http://lucene.472066.n3.nabble.com/dataimport-properties-is-not-updated-on-delta-import-td916753.html
* https://wiki.apache.org/solr/DataImportHandlerDeltaQueryViaFullImport
* https://www.searchstax.com/blog/importing-data-postgresql-solr/
