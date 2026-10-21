# Milestone 3 — Live TV and guide

Status: **complete**, with real-device validation still outstanding.

## Delivered

- Dense three-pane Live TV browser: category rail, searchable channel table, preview
  and selected-channel panel.
- Category counts for All channels, Favorites, Recently watched and language → genre
  groups.
- Favourites persisted through the core `FavoriteDao`.
- Recently watched backed by `HistoryDao`.
- Now/next from the imported XMLTV guide.
- Four-hour TV Guide grid with current-programme highlighting.
- Guide refresh and empty-state handling.
- Debug source import for local testing.

## Verification

- Live TV and guide rendered and navigated on the API 36 Android TV emulator.
- Synthetic XMLTV (`tools/fault-server/guide.xml`) imported and displayed programme
  blocks for both test channels.
- Favourites and recents exercised on the emulator.

## Notes

Guide data depends on the user's XMLTV feed. If no guide is imported, the Guide screen
explains how to add one. Very large guides are read in time-bounded queries rather than
loaded wholesale.
