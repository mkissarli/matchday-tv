# Milestone 5 — real football schedule and football taxonomy

Status: **complete**.

## Delivered

- ESPN keyless football feed (`soccer/all`) as the primary soccer source.
- Explicit football-vs-American-football separation:
  - `football` is association football: European, English and Latin American.
  - `american-football` is NFL/NCAA and never appears under Football.
  - Parser and hint tests cover `Soccer`, `NFL`, `NCAA Football`, `Premier League`,
    `Copa Libertadores` and friends.
- Schedule cache on disk: last-good fixtures survive provider rate limits and cold
  starts.
- Full ESPN soccer league id → name map (219 competitions) so match cards and league
  sections show real competition names rather than “Football”.
- Date-labelled schedule cards and a seven-day window.

## Verification

- Real ESPN football payloads parsed into 600+ events on the emulator.
- Football tab contains soccer only; American football stays separate.
- Saved schedule rendered while the provider was unavailable.

## Notes

ESPN's free keyless feed can change shape. Parsing is isolated in `EspnSoccerParser`
with JVM fixtures, so a provider change is a parser/test change rather than a UI change.
