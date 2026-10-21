# Testing on the PC

Use the **debug** APK on the x86_64 emulator. The public release APK contains only
`armeabi-v7a` and `arm64-v8a`, so it cannot run on the normal PC emulator.

## 1. Start the Android TV emulator

The AVD is named `MatchdayTV`.

```powershell
$env:ANDROID_HOME = "C:\Users\Thief3\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd MatchdayTV -no-snapshot-load
```

Wait until the TV home screen appears. Check the connection:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

## 2. Build and install

The default `JAVA_HOME` on this machine points at an old Java 8 install, so set it
explicitly:

```powershell
$env:JAVA_HOME = "C:\Users\Thief3\AppData\Local\Android\JDK\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "C:\Users\Thief3\AppData\Local\Android\Sdk"

.\gradlew.bat :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n dev.matchday.tv.debug/dev.matchday.tv.MainActivity
```

Or open the project in Android Studio, select the `MatchdayTV` device and press Run.

## 3. Start the local test server

Use a second terminal:

```powershell
cd C:\Users\Thief3\Documents\MyThings\programming\tv
python tools/fault-server/server.py --port 8088
```

Smoke-test it:

```powershell
python tools/fault-server/test_server.py
```

Available URLs from the emulator:

| URL | Purpose |
|---|---|
| `http://10.0.2.2:8088/playlist.m3u` | Two healthy channels: HLS + raw MPEG-TS |
| `http://10.0.2.2:8088/fault-playlist.m3u` | First variant returns 404, second is healthy |
| `http://10.0.2.2:8088/guide.xml` | Synthetic XMLTV guide |
| `http://10.0.2.2:8088/fault/404` | Injected 404 |
| `http://10.0.2.2:8088/fault/stall` | Sends a few KB then stalls |
| `http://10.0.2.2:8088/fault/slow` | Throttled stream |
| `http://10.0.2.2:8088/fault/drop` | Closes mid-stream |

## 4. Import the playlist

In **Live TV**, the empty state is pre-filled with the emulator URL in debug builds.
Press **Import playlist**.

Or use **Settings → Add a source** and paste:

- Name: `Test playlist`
- Playlist URL: `http://10.0.2.2:8088/playlist.m3u`
- XMLTV EPG URL: `http://10.0.2.2:8088/guide.xml`

Or test phone setup from the PC browser:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" forward tcp:8765 tcp:8765
```

Open `http://127.0.0.1:8765` on the PC while the TV is on **Settings**. Enter the
pairing code shown on the TV and submit the URLs. This exercises the same LAN setup
flow as a phone.

## 5. Test checklist

### Live TV
- Wait for the catalogue to build; two channels should appear.
- Select **Test HLS** and press Enter. Expect colour bars, “Playing”, 360P.
- Press Back, select **Test MPEG-TS**, press Enter. Expect the same picture through
  the raw TS path.
- Press Enter while playing: the status should change to **Paused**; press again to
  resume.
- Add a channel to Favourites, open **Favorites**, and confirm it remains after
  leaving and returning to Live TV.
- Open **Recently watched** after playing a channel.

### EPG
- Open **Guide**. The synthetic guide should show `HLS Show 2/3/4` and
  `TS Show 2/3/4`.
- The current programme block should be highlighted.
- Open Live TV and confirm the channel rows show now/next titles.

### Playback failover
- Import `http://10.0.2.2:8088/fault-playlist.m3u` as a second source.
- Play **Fault failover**. The first request returns 404; the player should retry,
  switch to the healthy TS variant and start playing.
- Watch the server terminal: the sequence should be `/fault/404`, then
  `/live/ok.ts`.

### Sports Hub
- Wait a few seconds for the schedule load.
- Expect Football fixtures from ESPN and other sports from TheSportsDB.
- Select **Football** and confirm only soccer appears. **American football** stays a
  separate tab.
- Use the search/refresh controls and open **Find channels** on an event.
- Turn off the PC network and restart the app; the last-good schedule should still
  appear with a “Saved schedule” label.

### Phone setup / source management
- On Settings, confirm the LAN URL and pairing code appear.
- Submit a playlist from the PC browser through the forwarded port.
- Confirm the source appears in **Sources** and the channels appear in Live TV.
- Change the playback quality ceiling; reopen the player and check the setting
  persists.

## 6. Useful diagnostics

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb logcat -c
& $adb logcat | Select-String "mpv|OwnTVPlayer|EpgSync|SourceImporter"
```

Screenshot:

```powershell
.\scripts\screenshot.ps1 -Out captures\my-test.png
```

Direct player launch, bypassing the library:

```powershell
& $adb shell am start -n dev.matchday.tv.debug/dev.matchday.tv.MainActivity `
  -e debug_url http://10.0.2.2:8088/live/ok.ts
```

## What the PC emulator cannot prove

- Real Fire TV armv7 / Google TV arm64 decode behaviour.
- HDMI mode switching, surround/audio passthrough and real remote media keys.
- Memory pressure on a 1 GB Fire TV Stick.
- Wi-Fi/LAN performance from a phone to a real TV.
