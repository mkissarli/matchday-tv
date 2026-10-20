# Third-party notices

Matchday is GPLv3. This file records the main third-party components and where their source/licence information lives. It is not a substitute for the full licence texts shipped by each dependency.

## OwnTV Core / player-core

- Project: https://github.com/ahXN00/OwnTV_Core
- Licence: GNU GPL v3.0
- Pinned version: `core-1.0.68`
- Source in this repository: `third_party/owntv-core` (git submodule)
- Published artifacts used by the app:
  - `tv.own.owntv:core:1.0.68`
  - `tv.own.owntv:player-core:1.0.68`

Matchday uses the headless engine (database, sync, parsers, EPG, playback engines, watchdogs, companion setup). The Matchday UI is a new shell and does not reuse OwnTV's screen design.

## libmpv / FFmpeg

- libmpv is GPLv2-or-later. The FFmpeg build's exact licence depends on its configure flags.
- OwnTV's Android libmpv packaging: https://github.com/ahXN00/OwnTV_libmpv
- libmpv: https://github.com/mpv-player/mpv
- FFmpeg: https://ffmpeg.org/

A full binary-distribution audit, including the exact source/commit and any written offer required by GPL/LGPL, is still pending. Do not publish a release APK until that audit is complete.

## AndroidX / Jetpack

- AndroidX, Compose, Media3, Room, WorkManager, Lifecycle: Apache License 2.0
- https://developer.android.com/jetpack/androidx

## Kotlin / coroutines

- Kotlin and kotlinx.coroutines: Apache License 2.0
- https://kotlinlang.org/

## Koin

- Koin: Apache License 2.0
- https://insert-koin.io/

## OkHttp

- OkHttp: Apache License 2.0
- https://square.github.io/okhttp/

## Coil

- Coil: Apache License 2.0
- https://coil-kt.github.io/coil/

## Test media

The HLS/MPEG-TS files under `tools/fault-server/media` are generated locally from FFmpeg's `testsrc2` and `sine` sources and are development-only. They are not shipped content.
