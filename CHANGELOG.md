# Changelog
All notable changes to this project will be documented in this file.

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
