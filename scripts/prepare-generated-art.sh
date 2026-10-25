#!/usr/bin/env bash
# Runtime encodings only: no repainting, compositing or enlargement. Originals stay intact.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p public/launcher/art
convert design-assets/s49/originals/dusk-lake.png -strip -quality 92 public/launcher/art/dusk-lake-v1.webp
convert design-assets/s49/originals/dusk-lake.png -resize 480x -strip -quality 85 public/launcher/art/dusk-lake-thumb-v1.webp
convert design-assets/s49/originals/memory-archive.png -resize 512x512 -strip -quality 90 public/launcher/art/memory-archive-v1.webp
convert design-assets/s49/originals/glass-swallow.png -resize 384x384 -strip -quality 90 public/launcher/art/glass-swallow-v1.webp
identify -format '%f %wx%h %[channels] %b\n' public/launcher/art/*.webp
