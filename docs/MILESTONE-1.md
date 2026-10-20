# Milestone 1 — skeleton, import, playback

Status: **working on the Android TV emulator**, with the remaining cleanup listed below.

## What works

- New Compose for TV shell, Matchday Editorial theme, sport/live/guide/settings navigation.
- OwnTV Core and player-core integrated as the headless engine.
- First-run profile creation.
- M3U URL import, persisted in Room through core's `SourceImporter`.
- Channel list from the imported source.
- HLS playback.
- Raw MPEG-TS playback.
- Debug-only direct player launch:
  ```bash
  adb shell am start -n dev.matchday.tv.debug/dev.matchday.tv.MainActivity \
    -e debug_url http://10.0.2.2:8088/live/ok.ts
  ```
- Synthetic HLS/TS test server in `tools/fault-server`.

## What was verified on the emulator

- API 36 Android TV x86_64, 1080p.
- M3U import of two synthetic channels.
- HLS first frame and playback.
- Raw TS first frame and playback through libmpv at 25 fps, hardware decode, 0 frame drops.
- D-pad focus on the channel list and player exit with Back.

## What is not verified on real hardware yet

- Fire OS 7 armv7 and Google TV arm64 decode paths.
- 4K/HEVC, audio passthrough, and HDMI mode switching.
- Memory and cold-start budgets on a 1 GB Fire TV Stick.
- Real remote key behaviour (media keys, channel up/down, numeric entry).
- libmpv memory/decoder behaviour on low-end hardware.

## Known issues

- The ExoPlayer live path reconnects a healthy picture on the emulator because its frame watchdog does not see frames from a `TextureView`. libmpv is the working path for now. Fix the watchdog before making ExoPlayer the default.
- The synthetic test server's full-file responses still produce harmless ffmpeg "stream ends prematurely" / corrupt-packet warnings while probing. Playback is unaffected; clean this up before fault-injection work.
- Focus after returning from the player lands on the first channel rather than the one just watched.
- The Sports screen shows a debug-only synthetic design preview; no schedule provider is connected yet.
- Release signing and a release APK are not configured; CI currently publishes a debug APK.
