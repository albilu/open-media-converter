#!/bin/bash
# Build the required GTK API on Ubuntu 22.04's glibc, for both X11 and Wayland.
# Java-GI uses FFM directly, so runtime GObject introspection is unnecessary.
set -euo pipefail
PREFIX=/opt/omc-gtk
BUILD_ROOT=$(mktemp -d)
trap 'rm -rf "$BUILD_ROOT"' EXIT
cd "$BUILD_ROOT"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig"
export LD_LIBRARY_PATH="$PREFIX/lib"
export PATH="$PREFIX/bin:$PATH"
mkdir -p "$PREFIX/share/doc/omc-gtk"

fetch() {
    local name=$1 url=$2 sha=$3
    curl -fL --retry 3 "$url" -o "$name.tar.xz"
    printf '%s  %s\n' "$sha" "$name.tar.xz" | sha256sum -c -
    mkdir "$name"
    tar -xf "$name.tar.xz" --strip-components=1 -C "$name"
    printf '%s  %s\n' "$sha" "$url" >> "$PREFIX/share/doc/omc-gtk/sources.txt"
    cp "$name/COPYING" "$PREFIX/share/doc/omc-gtk/$name-COPYING"
}

build() {
    local name=$1
    shift
    meson setup "$name/build" "$name" --prefix="$PREFIX" --libdir=lib \
        --buildtype=release --strip --wrap-mode=nofallback "$@"
    # Bound memory use on CI runners and developer machines.
    meson compile -C "$name/build" -j 4
    meson install -C "$name/build"
}

fetch glib https://download.gnome.org/sources/glib/2.80/glib-2.80.5.tar.xz \
    9f23a9de803c695bbfde7e37d6626b18b9a83869689dd79019bf3ae66c3e6771
build glib -Dtests=false -Dintrospection=disabled -Ddocumentation=false \
    -Dman-pages=disabled -Dsysprof=disabled -Dselinux=disabled

fetch wayland https://gitlab.freedesktop.org/wayland/wayland/-/releases/1.22.0/downloads/wayland-1.22.0.tar.xz \
    1540af1ea698a471c2d8e9d288332c7e0fd360c8f1d12936ebb7e7cbc2425842
build wayland -Dtests=false -Ddocumentation=false

fetch wayland-protocols https://gitlab.freedesktop.org/wayland/wayland-protocols/-/releases/1.32/downloads/wayland-protocols-1.32.tar.xz \
    7459799d340c8296b695ef857c07ddef24c5a09b09ab6a74f7b92640d2b1ba11
build wayland-protocols -Dtests=false

fetch gtk https://download.gnome.org/sources/gtk/4.14/gtk-4.14.5.tar.xz \
    5547f2b9f006b133993e070b87c17804e051efda3913feaca1108fa2be41e24d
build gtk -Dintrospection=disabled -Ddocumentation=false -Dman-pages=false \
    -Dbuild-demos=false -Dbuild-examples=false -Dbuild-tests=false \
    -Dbuild-testsuite=false -Dmedia-gstreamer=disabled -Dprint-cups=disabled \
    -Dvulkan=disabled -Dsysprof=disabled -Dtracker=disabled

pkg-config --atleast-version=4.14.5 gtk4
