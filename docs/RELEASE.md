# Release notes

## Signing

A public release must be signed with a keystore that is **not** committed to this repo.

1. Create a keystore once and back it up somewhere safe:

   ```bash
   keytool -genkeypair -v -keystore matchday-release.jks \
     -alias matchday -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these GitHub repository secrets:

   - `MATCHDAY_KEYSTORE_BASE64` — `base64 -w0 matchday-release.jks`
   - `MATCHDAY_KEYSTORE_PASSWORD`
   - `MATCHDAY_KEY_ALIAS` (`matchday`)
   - `MATCHDAY_KEY_PASSWORD`

3. Push a tag:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

   The `Android` workflow builds the release APK, names it
   `matchday-0.1.0.apk`, writes a SHA-256 checksum and publishes both on the
   GitHub release.

## Local release

```bash
export MATCHDAY_KEYSTORE=matchday-release.jks
export MATCHDAY_KEYSTORE_PASSWORD=...
export MATCHDAY_KEY_ALIAS=matchday
export MATCHDAY_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Without secrets the release variant falls back to debug signing, which is only
suitable for local testing.

## Pre-release checks

- `./gradlew :domain:test`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleRelease`
- Verify the APK signature with `apksigner verify --print-certs`
- Confirm `THIRD_PARTY_NOTICES.md` still matches the native libmpv/FFmpeg build
- Install on a Fire OS 7 device if possible; emulator coverage is not enough
