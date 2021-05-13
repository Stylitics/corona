# Changelog
All notable changes to this project will be documented in this file.

## [0.1.13-SNAPSHOT] - 2021-05-13
- Reverted ability to query using `POST` introduced in 0.1.11

## [0.1.12-SNAPSHOT] - 2021-05-11

### Changed
- Replaced `clojure.data.json` with [jsonista](https://github.com/metosin/jsonista) making `edn->json` about 8 times faster! Thanks @visibletrap
- Changed `http-kit` version to `2.4.0`. Thanks @sonwh98

## [0.1.11-SNAPSHOT] - 2020-08-27

### Changed
- Throw more informative error on JSON parse failure. Thanks @AdamFrey
- Added ability to query using `POST` method (with `POST` there is no query length limitation unlike `GET`). Corona uses `POST` by default (to use `GET` attach `{:method :get}` to the settings). Thanks @sonwh98

## [0.1.10-SNAPSHOT] - 2019-12-12

### Added
- Added dynamic var `corona.utils/*json-read-throw-on-error*` where if set to true, `corona.utils/json-read-str` used to parse solr responses will throw exception, instead of returning nil when parsing JSON fails.

## [0.1.9-SNAPSHOT] - 2019-08-01

### Changed
- `corona.core-admin/update!` can handle list of JSON commands correctly and accepts optional settings as 3d argument, exactly like `corona.core-admin/add!` and `corona.core-admin/delete!`
- `corona.core-admin/add!` sends single doc to the `/update/json/docs` Solr endpoint as specified in the Solr documentation.

## [0.1.8-SNAPSHOT] - 2019-04-30
### Fixed
- `corona.query/terms-per-field->q` had to wrap term with quotes in case term has more than one word.
## [0.1.7-SNAPSHOT] - 2019-04-27
### Added
- `corona.core-admin/rename!`
- `corona.core-admin/swap!`
- `corona.core-admin/merge-indexes!`
- `corona.core-admin/split!`
- `corona.core-admin/request-status`
- `corona.core-admin/request-recovery`
### Changed
`corona.core-admin/delete!` is renamed `corona.core-admin/unload!` to match solr core-admin API

## [0.1.6-SNAPSHOT] - 2019-04-26
### Added
- `corona.core-admin/update!` general reusable handler,
- `corona.core-admin/reload!` handler

## [0.1.5-SNAPSHOT] - 2019-04-26
### Changed
- `query-mlt-tv-edismax` now doesn't append interesting terms in `:q` but rather make them accessible via special var mltq you can call like this `${mltq}`. This allows more control on how to add the special query.

## [0.1.4-SNAPSHOT] - 2019-04-23
### Changed
- `query-mlt-tv-edismax` now uses lucene query parser for interesting terms and can accept more then one id-boost pair e.g. `{:mlt.field "id" :mlt.ids [["12345" 3] ["12346" "2"]]}`

## [0.1.3] - 2019-04-20
### Changed
- emacs install doc fix
- url-encode for feature extraction request

## [0.1.2] - 2019-04-18
### Changed
- mlt-tv-edismax: remove mlt.q (id) via filter query instead of regular query, to make sure it is removed (no disjunction)

### Changed
- http-kit post content-type headers fix

## [0.1.2-SNAPSHOT] - 2019-04-16

### Changed
- http-kit post content-type headers fix

### Removed
- reset! in `corona.index` no longer is 

## [0.1.1-SNAPSHOT] - 2019-04-15

### Changed
- clj-http -> http-kit
- `corona.client` -> `corona.core-admin` + `corona.index` 
- main query API as moved from `corona.client` to `corona.query`

### Added
- Some docs in `corona.core-admin`
