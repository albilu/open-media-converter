#!/bin/bash
# Package builder for Open Media Converter (GTK4).
# Builds the shaded jar + a self-contained jlink runtime, then assembles
# .deb / .rpm / .pkg.tar.zst / .AppImage installers. Run inside the omc-dev
# Docker image (or any Linux with JDK 23, maven, dpkg-deb, rpmbuild, bsdtar).
set -euo pipefail

# Package metadata and the shaded native libraries target Linux amd64.
if [[ "$(uname -s):$(uname -m)" != "Linux:x86_64" ]]; then
    echo "Packaging requires Linux amd64 (x86_64); use an amd64 Docker environment." >&2
    exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROJECT_VERSION="$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout)"
DEFAULT_VERSION="${PROJECT_VERSION%-SNAPSHOT}"
VERSION="${1:-$DEFAULT_VERSION}"
if [[ ! "$VERSION" =~ ^[0-9]+([.][0-9]+){1,3}$ ]]; then
    echo "Invalid package version: expected numeric dotted version" >&2
    exit 2
fi
if [[ "$VERSION" != "$PROJECT_VERSION" && "$VERSION" != "$DEFAULT_VERSION" ]]; then
    echo "Package version $VERSION must match Maven version $PROJECT_VERSION" >&2
    exit 2
fi
STAGE="$ROOT/packaging/stage"
RUNTIME="$STAGE/opt/open-media-converter/runtime"
APP="$STAGE/opt/open-media-converter"
JAR="$ROOT/omc-gtk/target/open-media-converter-${PROJECT_VERSION}.jar"

log() { echo "[omc-package] $*"; }

# Modules from jdeps over the shaded jar (+ crypto/naming/management for
# TLS and runtime introspection). java-gi extracts native libraries through
# zipfs and Jackson uses Unsafe; these dynamically loaded modules are not
# reported by jdeps. jdk.localedata ships non-English locale data.
JDK_MODULES="java.base,java.desktop,java.sql,java.logging,java.net.http,jdk.crypto.ec,jdk.zipfs,jdk.unsupported,jdk.localedata,java.naming,java.management"

log "Building shaded jar..."
# Packaging deliberately skips tests, so it must also skip JaCoCo's test
# coverage gate. Otherwise stale jacoco.exec data from an earlier test run can
# make a release build fail even though no tests execute here.
mvn -q -pl omc-gtk -am package -DskipTests -Djacoco.skip=true

log "Assembling application tree under $STAGE..."
rm -rf "$STAGE"
mkdir -p "$APP" "$RUNTIME" "$STAGE/usr/bin" \
    "$STAGE/usr/share/applications" \
    "$STAGE/usr/share/metainfo" \
    "$STAGE/usr/share/man/man1" \
    "$STAGE/usr/share/doc/open-media-converter" \
    "$STAGE/usr/share/licenses/open-media-converter" \
    "$STAGE/usr/share/icons/hicolor"

cp "$JAR" "$APP/omc.jar"
cp LICENSE "$STAGE/usr/share/licenses/open-media-converter/LICENSE"
cp LICENSE "$STAGE/usr/share/doc/open-media-converter/copyright"

log "Creating jlink runtime (modules: $JDK_MODULES)..."
rm -rf "$RUNTIME"
jlink --add-modules "$JDK_MODULES" \
    --strip-debug --no-header-files --no-man-pages --compress zip-6 \
    --output "$RUNTIME"
runtime_modules=$("$RUNTIME/bin/java" --list-modules | cut -d@ -f1)
grep -qx 'java.base' <<<"$runtime_modules"
required_modules=$(jdeps --ignore-missing-deps --multi-release 23 \
    --print-module-deps "$APP/omc.jar")
for module in ${required_modules//,/ }; do
    if ! grep -qx "$module" <<<"$runtime_modules"; then
        echo "Bundled runtime is missing required module: $module" >&2
        exit 1
    fi
done

cat > "$STAGE/usr/bin/open-media-converter" <<'EOF'
#!/bin/sh
# OMC launcher: prefer the bundled runtime, fall back to system java
APP_HOME=/opt/open-media-converter
if [ -x "$APP_HOME/runtime/bin/java" ]; then
    exec "$APP_HOME/runtime/bin/java" --enable-native-access=ALL-UNNAMED -jar "$APP_HOME/omc.jar" "$@"
else
    exec java --enable-native-access=ALL-UNNAMED -jar "$APP_HOME/omc.jar" "$@"
fi
EOF
chmod 755 "$STAGE/usr/bin/open-media-converter"

cp packaging/resources/open-media-converter.desktop "$STAGE/usr/share/applications/open-media-converter.desktop"
cp packaging/resources/open-media-converter.metainfo.xml "$STAGE/usr/share/metainfo/open-media-converter.metainfo.xml"
cp packaging/resources/open-media-converter.1 "$STAGE/usr/share/man/man1/open-media-converter.1"
cp -a omc-gtk/src/main/resources/icons/hicolor/. "$STAGE/usr/share/icons/hicolor/"

# Rasterize PNG icons from the SVG artwork: AppStream compose (flatpak export)
# and several desktop theme consumers require raster icons, the repo ships
# SVGs only. Kept in sync with render-icons.sh's size list.
if command -v rsvg-convert >/dev/null; then
    for size in 16 24 32 48 64 128 256 512; do
        icon_dest="$STAGE/usr/share/icons/hicolor/${size}x${size}/apps"
        mkdir -p "$icon_dest"
        icon_src="$icon_dest/open-media-converter.svg"
        [ -f "$icon_src" ] || icon_src="$STAGE/usr/share/icons/hicolor/scalable/apps/open-media-converter.svg"
        rsvg-convert -w "$size" -h "$size" "$icon_src" \
            -o "$icon_dest/open-media-converter.png"
    done
fi

# The hicolor tree needs an index.theme to be a valid icon theme; without it
# GLib-based icon lookups (including appstreamcli compose) find no icons.
hicolor_dirs=$(cd "$STAGE/usr/share/icons/hicolor" && ls -d */apps | sed 's|/$||' | paste -sd,)
{
    echo "[Icon Theme]"
    echo "Name=Hicolor"
    echo "Comment=Fallback icon theme"
    echo "Hidden=true"
    echo "Directories=$hicolor_dirs"
    for d in ${hicolor_dirs//,/ }; do
        size="${d%%x*}"
        echo ""
        echo "[$d]"
        echo "Size=$size"
        if [ "$d" = "scalable/apps" ]; then
            echo "MinSize=16"
            echo "MaxSize=512"
            echo "Type=Scalable"
        else
            echo "Type=Fixed"
        fi
    done
} > "$STAGE/usr/share/icons/hicolor/index.theme"

log "Stage complete:"
du -sh "$STAGE" "$RUNTIME"

# ---- .deb ----
build_deb() {
    log "Building .deb..."
    local debroot="$STAGE-deb"
    rm -rf "$debroot"
    cp -r "$STAGE" "$debroot"
    mkdir -p "$debroot/DEBIAN"
    cp packaging/debian/control "$debroot/DEBIAN/control"
    cp packaging/debian/postinst "$debroot/DEBIAN/postinst" 2>/dev/null || true
    cp packaging/debian/prerm "$debroot/DEBIAN/prerm" 2>/dev/null || true
    [ -f "$debroot/DEBIAN/postinst" ] && chmod 755 "$debroot/DEBIAN/postinst"
    [ -f "$debroot/DEBIAN/prerm" ] && chmod 755 "$debroot/DEBIAN/prerm"
    sed -i "s/__VERSION__/${VERSION}/g" "$debroot/DEBIAN/control"
    dpkg-deb --root-owner-group --build -Zxz "$debroot" \
        "$ROOT/packaging/open-media-converter_${VERSION}_amd64.deb"
    rm -rf "$debroot"
    log "Built open-media-converter_${VERSION}_amd64.deb"
}

# ---- .rpm ----
build_rpm() {
    log "Building .rpm..."
    command -v rpmbuild >/dev/null || { log "rpmbuild not found, skipping rpm"; return 0; }
    local rpmtop="$ROOT/packaging/rpmbuild"
    rm -rf "$rpmtop"
    mkdir -p "$rpmtop"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
    sed -e "s/__VERSION__/${VERSION}/g" packaging/rpm/open-media-converter.spec \
        > "$rpmtop/SPECS/open-media-converter.spec"
    (cd "$rpmtop" && rpmbuild --define "_topdir $rpmtop" --define "stage $STAGE" \
        --nodeps --nocheck -bb "$rpmtop/SPECS/open-media-converter.spec")
    find "$rpmtop/RPMS" "$ROOT/rpmbuild/RPMS" -name "*.rpm" -exec mv {} "$ROOT/packaging/" \; 2>/dev/null || true
    rm -rf "$rpmtop" "$ROOT/rpmbuild"
}

# ---- Arch .pkg.tar.zst ----
build_arch() {
    log "Building .pkg.tar.zst..."
    local archroot="$ROOT/packaging/archbuild"
    rm -rf "$archroot"
    mkdir -p "$archroot/pkg"
    cp -a "$STAGE/." "$archroot/pkg/"
    chmod 755 "$archroot/pkg/usr/bin/open-media-converter"

    # Minimal pacman package: .PKGINFO + payload, compressed with zstd
    local size
    size=$(du -sk "$archroot/pkg" | cut -f1)
    local builddate
    builddate=$(date +%s)
    cat > "$archroot/pkg/.PKGINFO" <<EOF
pkgname = open-media-converter
pkgbase = open-media-converter
pkgver = ${VERSION}-1
pkgdesc = Native Linux media and document converter with GTK 4 UI (bundled Java runtime)
url = https://github.com/albilu/open-media-converter
arch = x86_64
license = GPL-3.0-or-later
depend = gtk4>=4.10
depend = glib2>=2.66.0
depend = gobject-introspection>=1.66.0
depend = imagemagick
depend = libreoffice-fresh
optdepend = ffmpeg: video/audio conversion (also embedded in the application jar)
optdepend = pandoc: document conversion (also embedded in the application jar)
packager = Open Media Converter Team <maintainer@openmediaconverter.org>
size = $((size * 1024))
builddate = ${builddate}
EOF
    cat > "$archroot/pkg/.BUILDINFO" <<EOF
format = 2
pkgname = open-media-converter
pkgbase = open-media-converter
pkgver = ${VERSION}-1
pkgarch = x86_64
pkgbuild_sha256sum = $(sha256sum packaging/arch/PKGBUILD | cut -d' ' -f1)
packager = Open Media Converter Team <maintainer@openmediaconverter.org>
builddate = ${builddate}
builddir = /app
startdir = /app
buildtool = omc-package-builder
buildtoolver = 1.0.0
buildenv = !distcc
buildenv = color
buildenv = !ccache
buildenv = check
buildenv = !sign
options = strip
options = docs
options = !libtool
options = !staticlibs
options = emptydirs
options = zipman
options = purge
options = !debug
options = lto
EOF
    (cd "$archroot/pkg" && LANG=C bsdtar -czf .MTREE --format=mtree \
        --uid 0 --gid 0 \
        --options='!all,use-set,type,uid,gid,mode,time,size,sha256,link' \
        .PKGINFO .BUILDINFO opt usr)
    (cd "$archroot/pkg" && tar -C "$archroot/pkg" \
        --owner=0 --group=0 --numeric-owner \
        --use-compress-program="zstd -19 -T0" \
        -cf "$ROOT/packaging/open-media-converter-${VERSION}-1-x86_64.pkg.tar.zst" \
        .PKGINFO .BUILDINFO .MTREE opt usr)
    rm -rf "$archroot"
    log "Built open-media-converter-${VERSION}-1-x86_64.pkg.tar.zst"
}

# ---- AppImage ----
build_appimage() {
    log "Building .AppImage..."
    local build_dir="$ROOT/packaging/appimage-build"
    local appdir="$build_dir/AppDir"
    local downloads="$build_dir/downloads"
    local appimage="$ROOT/packaging/Open_Media_Converter-${VERSION}-x86_64.AppImage"
    local triplet="x86_64-linux-gnu"
    rm -rf "$appdir"
    mkdir -p "$appdir" "$downloads"

    # Reuse the staged tree: bundled jlink runtime + shaded jar + desktop data.
    cp -a "$STAGE/." "$appdir/"

    # Bundle GTK 4 libraries for portability to systems without GTK 4.
    log "Bundling GTK 4 libraries..."
    mkdir -p "$appdir/usr/lib/${triplet}/girepository-1.0" \
        "$appdir/usr/lib/${triplet}/gdk-pixbuf-2.0/2.10.0/loaders"
    local gtk_libs=(
        "libgtk-4.so.1"
        "libglib-2.0.so.0"
        "libgobject-2.0.so.0"
        "libgio-2.0.so.0"
        "libgdk_pixbuf-2.0.so.0"
        "libpango-1.0.so.0"
        "libpangocairo-1.0.so.0"
        "libcairo.so.2"
        "libcairo-gobject.so.2"
        "libharfbuzz.so.0"
        "libgraphene-1.0.so.0"
        "libepoxy.so.0"
        "libfontconfig.so.1"
        "libfreetype.so.6"
        "libpng16.so.16"
    )
    local copied=0
    for lib in "${gtk_libs[@]}"; do
        local lib_path
        lib_path=$(ldconfig -p | grep "$lib" | grep -i "x86-64" | awk '{print $NF}' | head -1)
        if [ -n "$lib_path" ] && [ -f "$lib_path" ]; then
            cp -P "$lib_path" "$appdir/usr/lib/${triplet}/"
            local lib_dir lib_base
            lib_dir=$(dirname "$lib_path")
            lib_base=$(basename "$lib" | cut -d. -f1,2,3)
            cp -P "$lib_dir/$lib_base"* "$appdir/usr/lib/${triplet}/" 2>/dev/null || true
            copied=$((copied + 1))
        else
            log "  Library not found: $lib (will use system fallback)"
        fi
    done
    log "  Bundled $copied GTK libraries"

    local typelib_dir="/usr/lib/${triplet}/girepository-1.0"
    if [ -d "$typelib_dir" ]; then
        for typelib in Gtk-4.0 GLib-2.0 GObject-2.0 Gio-2.0 Gdk-4.0 GdkPixbuf-2.0 \
                       Pango-1.0 cairo-1.0 HarfBuzz-0.0 Graphene-1.0; do
            cp "$typelib_dir/$typelib.typelib" \
                "$appdir/usr/lib/${triplet}/girepository-1.0/" 2>/dev/null || true
        done
    fi
    local pixbuf_loaders="/usr/lib/${triplet}/gdk-pixbuf-2.0/2.10.0/loaders"
    if [ -d "$pixbuf_loaders" ]; then
        cp -r "$pixbuf_loaders"/* \
            "$appdir/usr/lib/${triplet}/gdk-pixbuf-2.0/2.10.0/loaders/" 2>/dev/null || true
        if command -v gdk-pixbuf-query-loaders >/dev/null; then
            GDK_PIXBUF_MODULEDIR="$appdir/usr/lib/${triplet}/gdk-pixbuf-2.0/2.10.0/loaders" \
                gdk-pixbuf-query-loaders \
                > "$appdir/usr/lib/${triplet}/gdk-pixbuf-2.0/2.10.0/loaders.cache" 2>/dev/null || true
        fi
    fi

    cp packaging/appimage/AppRun "$appdir/AppRun"
    chmod 755 "$appdir/AppRun"
    ln -sf "usr/share/applications/open-media-converter.desktop" \
        "$appdir/open-media-converter.desktop"
    ln -sf "usr/share/icons/hicolor/scalable/apps/open-media-converter.svg" \
        "$appdir/open-media-converter.svg"

    # appimagetool: download if not installed
    local appimagetool_url="https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage"
    local appimagetool_cmd
    if command -v appimagetool >/dev/null; then
        appimagetool_cmd="appimagetool"
    else
        appimagetool_cmd="$downloads/appimagetool"
        if [ ! -x "$appimagetool_cmd" ]; then
            log "Downloading appimagetool..."
            if command -v wget >/dev/null; then
                wget -q -O "$appimagetool_cmd" "$appimagetool_url"
            else
                curl -L -s -o "$appimagetool_cmd" "$appimagetool_url"
            fi
            chmod +x "$appimagetool_cmd"
        fi
    fi

    # Use --appimage-extract-and-run when FUSE is unavailable (Docker/CI);
    # a privileged container exposes /dev/fuse but still lacks libfuse.so.2
    local extract_flag=""
    if [ ! -e /dev/fuse ] || [ ! -w /dev/fuse ] || [ -n "${GITHUB_ACTIONS:-}" ] || [ -f /.dockerenv ]; then
        extract_flag="--appimage-extract-and-run"
    fi

    rm -f "$appimage"
    set +e
    ARCH=x86_64 "$appimagetool_cmd" $extract_flag "$appdir" "$appimage" \
        > "$build_dir/appimagetool.log" 2>&1
    local tool_exit=$?
    set -e
    if [ ! -f "$appimage" ]; then
        echo "appimagetool failed (exit code: $tool_exit):" >&2
        cat "$build_dir/appimagetool.log" >&2
        exit 1
    fi
    chmod +x "$appimage"
    rm -rf "$appdir"
    log "Built Open_Media_Converter-${VERSION}-x86_64.AppImage"
}

# ---- Flatpak bundle ----
# GNOME runtime branch used by packaging/flatpak/io.github.albilu.OpenMediaConverter.yml
FLATPAK_RUNTIME_VERSION="50"

build_flatpak() {
    log "Building .flatpak..."
    if ! command -v flatpak-builder >/dev/null; then
        echo "flatpak-builder not found; install flatpak-builder or use the omc-dev image" >&2
        exit 1
    fi
    # Build under $HOME, not $ROOT: flatpak-builder reserves /app inside its
    # sandbox and refuses to share a build directory that lives there.
    local fb_dir="$HOME/.cache/omc-flatpak-build"
    local repo="$fb_dir/repo"
    local manifest="$ROOT/packaging/flatpak/io.github.albilu.OpenMediaConverter.yml"
    local bundle="$ROOT/packaging/Open_Media_Converter-${VERSION}-x86_64.flatpak"
    flatpak --user remote-add --if-not-exists flathub \
        https://flathub.org/repo/flathub.flatpakrepo >/dev/null
    flatpak --user install -y --noninteractive flathub \
        "org.gnome.Platform//${FLATPAK_RUNTIME_VERSION}" \
        "org.gnome.Sdk//${FLATPAK_RUNTIME_VERSION}" >/dev/null
    rm -rf "$fb_dir/build" "$repo"
    mkdir -p "$fb_dir"
    # --disable-rofiles-fuse: containers usually lack a FUSE device.
    # Run from $fb_dir: flatpak-builder reserves /app inside its sandbox and
    # refuses to share a state directory that lives there.
    (cd "$fb_dir" && flatpak-builder --user --force-clean --disable-rofiles-fuse \
        --repo="$repo" "$fb_dir/build" "$manifest")
    rm -f "$bundle"
    flatpak build-bundle "$repo" "$bundle" io.github.albilu.OpenMediaConverter
    rm -rf "$fb_dir/build" "$repo"
    log "Built Open_Media_Converter-${VERSION}-x86_64.flatpak"
}

build_deb
build_rpm
build_arch
build_appimage
build_flatpak

log "Artifacts:"
ls -la "$ROOT/packaging/"*.deb "$ROOT/packaging/"*.rpm \
    "$ROOT/packaging/"*.pkg.tar.zst "$ROOT/packaging/"*.AppImage \
    "$ROOT/packaging/"*.flatpak 2>/dev/null || true
log "Done."
