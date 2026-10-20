#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p media
ffmpeg -y -loglevel error \
  -f lavfi -i testsrc2=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 12 -c:v libx264 -preset veryfast -pix_fmt yuv420p -g 60 -sc_threshold 0 \
  -c:a aac -b:a 128k \
  -f hls -hls_time 2 -hls_list_size 0 \
  -hls_segment_filename media/seg%03d.ts media/ok.m3u8
ffmpeg -y -loglevel error \
  -f lavfi -i testsrc2=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 12 -c:v libx264 -preset veryfast -pix_fmt yuv420p -g 60 -sc_threshold 0 \
  -c:a aac -b:a 128k -f mpegts media/ok.ts
echo "generated tools/fault-server/media"
