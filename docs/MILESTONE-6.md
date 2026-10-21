# Milestone 6 — playback resilience and fault injection

Status: **complete**, with real-device validation still outstanding.

## Delivered

- Pure Kotlin `PlaybackSupervisor` with:
  - quality ceiling (Auto/4K/FHD/HD/SD),
  - variant fallback within a logical channel,
  - fixture-level failover to sibling channels,
  - retry cooldown and blacklist cap,
  - stable-playback reset,
  - language priority English → French → Arabic → German.
- UI integration: failed streams switch automatically and show a short notice.
- D-pad Enter pauses/resumes; Back exits; player overlay shows quality and status.
- Local fault-injection server:
  - healthy HLS and raw MPEG-TS,
  - `/fault-playlist.m3u` with a broken first variant,
  - `/fault/404`, `/fault/stall`, `/fault/slow`, `/fault/drop`,
  - synthetic XMLTV guide,
  - Python smoke tests.
- JVM tests for quality choice, channel failover, cooldowns, blacklist and caps.

## Verification

- 404 failover observed: first `/fault/404`, then `/live/ok.ts` playing.
- Pause/resume and player exit verified on the emulator.
- Fault-server smoke tests pass.

## Notes

Network conditions and decoder behaviour still need measurements on a real Fire TV
Stick. libmpv is the working default; the ExoPlayer watchdog false-positive remains a
known limitation.
