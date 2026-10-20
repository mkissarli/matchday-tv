# Matchday fault server

Synthetic HLS and raw MPEG-TS endpoints for development.

```bash
./tools/fault-server/generate_media.sh
python tools/fault-server/server.py --port 8088
```

Emulator playlist:

```text
http://10.0.2.2:8088/playlist.m3u
```

Real TV over ADB:

```bash
adb reverse tcp:8088 tcp:8088
```

then use:

```text
http://127.0.0.1:8088/playlist.m3u
```

Fault injection endpoints are added in milestone 6.
