#!/bin/bash
# Regenerate the checked-in PNGs from the SVG artwork after editing the logo.
# Requires Inkscape; ordinary builds consume the generated files directly.
set -euo pipefail
ICON_ROOT="$(cd "$(dirname "$0")/.." && pwd)/omc-gtk/src/main/resources/icons/hicolor"
ICON_NAME=open-media-converter
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
