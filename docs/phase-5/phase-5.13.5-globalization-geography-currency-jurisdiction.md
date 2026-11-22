# Phase 5.13.5 - Globalization, Geography, Currency & Jurisdiction Foundation

This phase consolidates the previously planned 5.13.5A through 5.13.7 work.

## Implemented scenarios

### Language
- Persian and English are first-class UI languages.
- Persian uses RTL and Ant Design fa_IR.
- English uses LTR and Ant Design en_US.
- Login has a language selector before authentication.
- Authenticated users have persistent language/country/currency/time-zone/theme preferences.
- Translation infrastructure is extensible for additional languages.

### Geography
- Country, administrative division and city are normalized master data.
- Property location is linked through foreign keys.
- Administrative divisions are recursive to support province/state/region models.
- GeoNames import downloads countryInfo, admin1 and populated-place data.
- Full city mode uses allCountries.zip and filters populated places (feature class P).
- Import history is auditable in global_master_data_import.

### Currency
- ISO currencies live in global_currency.
- Runtime bootstrap synchronizes the JDK ISO currency catalog.
- TOMAN is represented as an explicit non-ISO currency.
- Existing monetary tables retain currency_code compatibility while gaining currency_id foreign keys.
- Database triggers validate and synchronize currency_id.
- TOMAN -> IRR is a deterministic 10:1 relation.
- FX rates are timestamped and never rewrite historical analyses.

### Jurisdiction
- Knowledge sources, candidates, crawler sources, urban rules and learning events carry country/admin/city/language scope.
- Scope levels are GLOBAL, COUNTRY, ADMIN_DIVISION and CITY.
- Case jurisdiction is derived from its property.
- Crawler source matching is location aware.
- Regulation evaluation is location aware.
- Learning events and feedback-created knowledge candidates inherit case jurisdiction.
- Crawler-created knowledge candidates inherit crawler-source jurisdiction.

### Isolation scenarios
- Iran-scoped sources/rules do not match Canadian cases.
- Canada/Ontario/Toronto sources can be selected independently.
- Global sources remain available to every jurisdiction.
- City scope is more specific than administrative-division scope, which is more specific than country scope.
- Learning evidence remains human-review gated; location scoping does not auto-approve knowledge.

## Master data sources

Country and subdivision identifiers follow ISO 3166 concepts.
Currency codes follow ISO 4217 where applicable.
Geographic country/admin/city data is imported from GeoNames.
Locale behavior follows BCP 47 / CLDR-compatible locale tags used by browser Intl and Ant Design.

## Operations

GeoNames automatic import is disabled by default to avoid surprising first-start downloads.

Enable it with:

`APP_GLOBALIZATION_MASTER_DATA_AUTO_IMPORT=true`

Full worldwide populated-place import:

`APP_GLOBALIZATION_MASTER_DATA_FULL_CITIES=true`

Or call as an administrator:

`POST /api/v1/global/master-data/import/geonames`

The importer is idempotent and uses upsert semantics.