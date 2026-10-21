# Milestone 4 — schedule domain and Sports Hub

Status: **complete**.

## Delivered

- Provider-neutral schedule domain: participants, artwork, venue, broadcaster, status,
  start/end time. Deliberately no score, clock, standing or result fields.
- Data-driven `SportRegistry` with 13 sports, icon keys, ordering, keywords and
  participant styles. Adding a sport is a data operation, not an enum/route change.
- `TheSportsDbParser` for the free keyless TheSportsDB feed.
- `FixtureMatcher` scoring fixtures against channel names and EPG programmes.
- Dense Sports Hub with sport tabs, event detail, seven-day window, date-labelled cards
  and “Find channels”.
- Match dialog listing candidate channels for an event.
- Unit tests for schedule parsing, sport normalisation, registry integrity and fixture
  matching.

## Verification

- `:domain:test` passes.
- Sports Hub and event matching exercised on the emulator with a real provider.

## Notes

The free schedule feed is best-effort. Matchday shows schedule metadata, not live
scores, and gracefully falls back to a cached schedule.
