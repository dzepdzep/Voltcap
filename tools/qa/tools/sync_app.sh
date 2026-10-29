#!/bin/sh
# Copies the given src_copy-relative files (e.g. ui/Motion.kt) into app/ keeping each target's line endings.
cd "$(dirname "$0")/../../.." || exit 1
for f in "$@"; do
  dst="app/src/main/java/com/shelf/launcher/$f"
  if head -c 2000 "$dst" | grep -q "$(printf '\r')"; then sed 's/\r$//; s/$/\r/' "src_copy/$f" > "$dst"; else sed 's/\r$//' "src_copy/$f" > "$dst"; fi
done
