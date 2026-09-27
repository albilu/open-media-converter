#!/bin/bash
# Synchronize the application icons with the master logo and regenerate PNGs.
# Requires Inkscape. The 16px and 24px SVGs are simplified optical variants.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ICON_ROOT="$ROOT/omc-gtk/src/main/resources/icons/hicolor"
ICON_NAME=open-media-converter
MASTER="$ROOT/resources/$ICON_NAME.svg"

cp "$MASTER" "$ICON_ROOT/scalable/apps/$ICON_NAME.svg"
for size in 32 48 64 128 256; do
    cp "$MASTER" "$ICON_ROOT/${size}x${size}/apps/$ICON_NAME.svg"
done

for size in 16 24 32 48 64 128 256 512; do
    destination="$ICON_ROOT/${size}x${size}/apps"
    mkdir -p "$destination"
    source_svg="$destination/$ICON_NAME.svg"
    if [[ ! -f "$source_svg" ]]; then
        source_svg="$ICON_ROOT/scalable/apps/$ICON_NAME.svg"
    fi
    inkscape "$source_svg" \
        --export-type=png --export-width="$size" --export-height="$size" \
        --export-filename="$destination/$ICON_NAME.png" >/dev/null
done
