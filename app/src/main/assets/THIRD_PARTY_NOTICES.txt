# Third-party notices

Matchday is GPLv3. This file records the main third-party components and where their source and licence information lives. It is not a substitute for the full licence texts shipped by each dependency.

## OwnTV Core / player-core

- Project: https://github.com/ahXN00/OwnTV_Core
- Licence: GNU GPL v3.0
- Pinned version: `core-1.0.68`
- Source in this repository: `third_party/owntv-core` (git submodule, commit `c14e944b`)
- Published artifacts used by the app:
  - `tv.own.owntv:core:1.0.68`
  - `tv.own.owntv:player-core:1.0.68`

Matchday uses the headless engine (database, sync, parsers, EPG, playback engines, watchdogs, companion setup). The Matchday UI is a new shell and does not reuse OwnTV's screen design.

## libmpv / FFmpeg

Audited from the binaries shipped in `tv.own.owntv:libmpv:2026.10.3` and the upstream release notes:

- libmpv package: https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.3
- Pinned build sources: `buildscripts/include/depinfo.sh` at tag `v2026.10.3`
- libmpv/mpv: https://github.com/mpv-player/mpv, build string `mpv v0.41.0-1092-g3186d369f-dirty`
- FFmpeg: version `n9.0.2` (https://ffmpeg.org/), built with
  `--enable-gpl --enable-version3`, without `--enable-nonfree`
- Licence reported by the shipped libraries: **GPL version 3 or later**
- Source code for the exact libmpv/FFmpeg builds is available from the pinned
  `OwnTV_libmpv` tag above, including the build scripts and dependency pins. The
  corresponding Matchday application source is this repository (GPLv3).

The APK redistributes these GPLv3-or-later binaries. Anyone receiving the APK may
obtain the corresponding source from the links above and from this repository.
No non-free FFmpeg components (for example `--enable-nonfree` or FDK AAC) are
enabled in the audited build.

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

## Schedule data

- Football fixtures: ESPN public scoreboard endpoints. Schedule fields only; Matchday
  does not read scores, clocks or standings. ESPN terms and attribution apply.
- Other sports: TheSportsDB public API. Matchday uses the free, keyless endpoint. The
  app is schedule-only and stores no results.
- No schedule provider is bundled as channel content; fixtures are display metadata.

## Test media

The HLS/MPEG-TS files under `tools/fault-server/media` are generated locally from
FFmpeg's `testsrc2` and `sine` sources and are development-only. They are not shipped
content. The local fault server also generates synthetic XMLTV guide data at runtime.

## Material Symbols

- Sport and calendar icons: Material Symbols by Google, Apache License 2.0.
- https://fonts.google.com/icons
- Icons are converted from the supplied SVG paths into Android vector drawables; no font is bundled.

## Team and league artwork

Team crests, competition marks and channel logos are not bundled with Matchday. In
normal use, artwork comes from the user's configured schedule/provider and remains
subject to the rights of the clubs, leagues and broadcasters. If no artwork is
available, Matchday draws a monogram fallback.
