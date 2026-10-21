# Milestone 7 — setup, scale and release

Status: **complete**, shipping v0.1.0 and v0.1.1.

## Delivered

- Phone-assisted setup: TV-hosted LAN page, pairing code, playlist + optional XMLTV
  submission. No cloud relay or account.
- Functional Settings:
  - phone setup URL/code,
  - manual playlist and EPG import with inline success/error messages,
  - source list,
  - quality ceiling,
  - clear saved schedule,
  - debug catalogue entry.
- EPG source registration and WorkManager sync.
- 50,000-entry catalogue regression test; 60,000 channel snapshot limit; bulk EPG
  lookup during fixture matching.
- Release signing setup:
  - local keystore outside git,
  - GitHub Actions secrets for a stable signing identity,
  - tag-triggered signed release APK with SHA-256.
- GPLv3/libmpv/FFmpeg licence audit and updated third-party notices.
- Public repository and GitHub Releases:
  - https://github.com/mkissarli/matchday-tv

## Verification

- 60+ domain tests, lint, debug and signed release builds pass.
- Signed APK verified with `CN=Matchday`.
- Phone setup exercised from a PC browser through `adb forward`.
- Release v0.1.0 published; v0.1.1 adds the league/headline improvements.

## Remaining device validation

- Fire OS 7 armv7 and Google TV arm64 installation/playback.
- Real HDMI/audio mode switching, media keys and 1 GB RAM pressure.
