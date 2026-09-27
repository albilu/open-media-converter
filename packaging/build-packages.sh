#!/bin/bash
# Package builder for Open Media Converter (GTK4).
# Builds system-tool and bundled-tool jars + a self-contained jlink runtime, then assembles
# .deb / .rpm / .pkg.tar.zst / .AppImage installers. Run inside the omc-dev
# Docker image, which supplies the portable GTK bundle and packaging tools.
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
DIST_DIR="$ROOT/packaging/dist"
RUNTIME="$STAGE/opt/open-media-converter/runtime"
APP="$STAGE/opt/open-media-converter"
JAR="$ROOT/omc-gtk/target/open-media-converter-${PROJECT_VERSION}.jar"

log() { echo "[omc-package] $*"; }

mkdir -p "$DIST_DIR"

# Modules from jdeps over the shaded jar (+ crypto/naming/management for
# TLS and runtime introspection). java-gi extracts native libraries through
# zipfs and Jackson uses Unsafe; these dynamically loaded modules are not
# reported by jdeps. jdk.localedata ships non-English locale data.
JDK_MODULES="java.base,java.desktop,java.sql,java.logging,java.net.http,jdk.crypto.ec,jdk.zipfs,jdk.unsupported,jdk.localedata,java.naming,java.management"

log "Building native-package jar (system conversion tools)..."
# Packaging deliberately skips tests, so it must also skip JaCoCo's test
# coverage gate. Otherwise stale jacoco.exec data from an earlier test run can
# make a release build fail even though no tests execute here.
# Clean first: resources from a previous bundled build must never leak into
# native packages when the embedded-tools Maven profile is disabled.
mvn -q -pl omc-gtk -am clean package -DskipTests -Djacoco.skip=true -Domc.skipEmbeddedTools=true

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
# Gio notification backends resolve the application's desktop ID. Retain the
# existing launcher and provide its application-ID alias without a second menu entry.
sed '/^\[Desktop Entry\]$/a NoDisplay=true' packaging/resources/open-media-converter.desktop \
    > "$STAGE/usr/share/applications/org.omc.OpenMediaConverter.desktop"
cp packaging/resources/open-media-converter.metainfo.xml "$STAGE/usr/share/metainfo/open-media-converter.metainfo.xml"
cp packaging/resources/open-media-converter.1 "$STAGE/usr/share/man/man1/open-media-converter.1"
cp -a omc-gtk/src/main/resources/icons/hicolor/. "$STAGE/usr/share/icons/hicolor/"

# Rasterize PNG icons from the SVG artwork for desktop themes that require
# raster icons. Kept in sync with render-icons.sh's size list.
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
        "$DIST_DIR/open-media-converter_${VERSION}_amd64.deb"
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
    find "$rpmtop/RPMS" "$ROOT/rpmbuild/RPMS" -name "*.rpm" -exec mv {} "$DIST_DIR/" \; 2>/dev/null || true
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
depend = hicolor-icon-theme
depend = ffmpeg>=6.1
depend = pandoc>=3.1
depend = imagemagick
depend = libreoffice-fresh
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
        -cf "$DIST_DIR/open-media-converter-${VERSION}-1-x86_64.pkg.tar.zst" \
        .PKGINFO .BUILDINFO .MTREE opt usr)
    rm -rf "$archroot"
    log "Built open-media-converter-${VERSION}-1-x86_64.pkg.tar.zst"
}

# ---- AppImage ----
build_appimage() {
    log "Building .AppImage..."
    local build_dir="$ROOT/packaging/appimage-build"
    local appdir="$build_dir/AppDir"
    local appimage="$DIST_DIR/Open_Media_Converter-${VERSION}-x86_64.AppImage"
    rm -rf "$appdir"
    mkdir -p "$appdir"

    # Reuse the native stage's runtime and desktop data, then replace only its
    # jar. Keep STAGE's system-tool jar intact for native package verification.
    cp -a "$STAGE/." "$appdir/"
    log "Building AppImage jar (bundled FFmpeg, ffprobe and Pandoc)..."
    mvn -q -pl omc-gtk -am package -DskipTests -Djacoco.skip=true -Domc.skipEmbeddedTools=false
    cp "$JAR" "$appdir/opt/open-media-converter/omc.jar"

    # This dependency closure is built on Ubuntu 22.04, independently of the
    # newer system libraries used by native packages and development tests.
    cp -a /opt/omc-appimage/native/. "$appdir/"
    install -m 755 packaging/appimage/AppRun "$appdir/AppRun"
    # The portable desktop launcher must resolve through AppRun, not /usr/bin.
    sed -e 's|/usr/bin/open-media-converter|open-media-converter|g' \
        -e 's|^Categories=.*|Categories=AudioVideo;Audio;Video;GTK;|' \
        packaging/resources/open-media-converter.desktop > "$appdir/open-media-converter.desktop"
    cp "$appdir/open-media-converter.desktop" "$appdir/usr/share/applications/open-media-converter.desktop"
    sed '/^\[Desktop Entry\]$/a NoDisplay=true' "$appdir/open-media-converter.desktop" \
        > "$appdir/usr/share/applications/org.omc.OpenMediaConverter.desktop"
    cat > "$appdir/usr/bin/open-media-converter" <<'EOF'
#!/bin/sh
APPDIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
exec "$APPDIR/AppRun" "$@"
EOF
    chmod 755 "$appdir/usr/bin/open-media-converter"
    cp "$STAGE/usr/share/icons/hicolor/512x512/apps/open-media-converter.png" "$appdir/"
    ln -s open-media-converter.png "$appdir/.DirIcon"
    # The catalog's legacy lint needs *.appdata.xml; its name must match the ID.
    mv "$appdir/usr/share/metainfo/open-media-converter.metainfo.xml" \
        "$appdir/usr/share/metainfo/org.omc.open-media-converter.appdata.xml"
    desktop-file-validate "$appdir/open-media-converter.desktop"
    appstreamcli validate --no-net "$appdir/usr/share/metainfo/org.omc.open-media-converter.appdata.xml"
    bash /opt/omc-appimage/appdir-lint.sh "$appdir"
    python3 packaging/appimage/check-abi.py "$appdir"

    rm -f "$appimage"
    if ! ARCH=x86_64 /opt/omc-appimage/appimagetool --appimage-extract-and-run \
        --runtime-file /opt/omc-appimage/runtime-x86_64 "$appdir" "$appimage" \
        > "$build_dir/appimagetool.log" 2>&1; then
        cat "$build_dir/appimagetool.log" >&2
        exit 1
    fi
    test -s "$appimage"
    chmod +x "$appimage"
    rm -rf "$appdir"
    log "Built Open_Media_Converter-${VERSION}-x86_64.AppImage"
}

build_deb
build_rpm
build_arch

# Portable bundles need a private theme index. Native packages use the system
# hicolor-icon-theme package and must never claim ownership of its index.
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
        if [ "$d" = "scalable/apps" ]; then
            echo "Size=48"
            echo "MinSize=16"
            echo "MaxSize=512"
            echo "Type=Scalable"
        else
            echo "Size=$size"
            echo "Type=Fixed"
        fi
    done
} > "$STAGE/usr/share/icons/hicolor/index.theme"


build_appimage

log "Artifacts in $DIST_DIR:"
ls -la "$DIST_DIR/"*.deb "$DIST_DIR/"*.rpm \
    "$DIST_DIR/"*.pkg.tar.zst "$DIST_DIR/"*.AppImage 2>/dev/null || true
log "Done."
