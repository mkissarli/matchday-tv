# Milestone 2 — catalogue, normalisation and merging

Status: **complete**.

## Delivered

- New pure-Kotlin `:domain` module with no Android dependencies.
- `NameNormalizer`: display cleanup, merge key normalisation, quality/codec/tag parsing.
- `LanguageCatalog`: language and country detection from channel names/group titles.
- `GenreCatalog`: data-driven genre classification.
- `SportHintCatalog`: sport hints for channel names and groups.
- `CatalogBuilder`: raw → logical channel merging, confidence scores, variant ordering.
- Debug catalogue screen showing raw → logical, merge reasons, language and genre.
- JVM tests covering normalisation, quality, language/genre, merging and confidence.

## Verification

- `./gradlew :domain:test` passes.
- Debug catalog screen verified on the Android TV emulator.

## Notes

The merge policy favours precision over recall. Ambiguous cross-source merges are kept
but marked low-confidence for the debug view. The later scale test covers 50,000 raw
entries.
