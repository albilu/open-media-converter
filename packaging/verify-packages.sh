#!/bin/bash
# Validate the artifacts that will be attached to a release. Run in omc-dev.
set -euo pipefail
PACKAGE_ROOT="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PACKAGE_ROOT/dist"
VERSION="${1:?Pass the package version}"
[[ "$VERSION" =~ ^[0-9]+([.][0-9]+){1,3}$ ]] || exit 2
CHECK_ROOT="$(mktemp -d)"
trap 'rm -rf "$CHECK_ROOT"' EXIT
DEB="$DIST_DIR/open-media-converter_${VERSION}_amd64.deb"
RPM="$DIST_DIR/open-media-converter-${VERSION}-1.x86_64.rpm"
ARCH="$DIST_DIR/open-media-converter-${VERSION}-1-x86_64.pkg.tar.zst"
APPIMAGE="$DIST_DIR/Open_Media_Converter-${VERSION}-x86_64.AppImage"
for artifact in "$DEB" "$RPM" "$ARCH"; do test -s "$artifact"; done
[[ "$(dpkg-deb -f "$DEB" Version)" == "$VERSION" ]]
[[ "$(dpkg-deb -f "$DEB" Architecture)" == amd64 ]]
[[ "$(rpm --dbpath "$CHECK_ROOT/rpmdb" -qp --qf '%{VERSION}-%{RELEASE}' "$RPM")" == "$VERSION-1" ]]
[[ "$(rpm --dbpath "$CHECK_ROOT/rpmdb" -qp --qf '%{ARCH}' "$RPM")" == x86_64 ]]
rpm --dbpath "$CHECK_ROOT/rpmdb" -K --nosignature "$RPM"
rpm --dbpath "$CHECK_ROOT/rpmdb" -qp --qf '[%{FILEUSERNAME}:%{FILEGROUPNAME}\n]' "$RPM" > "$CHECK_ROOT/rpm-owners"
if grep -vqx 'root:root' "$CHECK_ROOT/rpm-owners"; then
    echo 'RPM contains non-root payload ownership' >&2; exit 1
fi
mkdir "$CHECK_ROOT/deb" "$CHECK_ROOT/rpm" "$CHECK_ROOT/arch"
dpkg-deb --fsys-tarfile "$DEB" > "$CHECK_ROOT/deb.tar"
zstd -dq "$ARCH" -o "$CHECK_ROOT/arch.tar"
python3 - "$CHECK_ROOT/deb.tar" "$CHECK_ROOT/arch.tar" <<'PY'
import sys, tarfile
for archive in sys.argv[1:]:
    with tarfile.open(archive) as package:
        assert all(m.uid == 0 and m.gid == 0 for m in package.getmembers()), archive
PY
tar -xf "$CHECK_ROOT/deb.tar" -C "$CHECK_ROOT/deb"
rpm2cpio "$RPM" | bsdtar -xf - -C "$CHECK_ROOT/rpm"
tar -xf "$CHECK_ROOT/arch.tar" -C "$CHECK_ROOT/arch"
grep -qx "pkgver = ${VERSION}-1" "$CHECK_ROOT/arch/.PKGINFO"
grep -qx 'arch = x86_64' "$CHECK_ROOT/arch/.PKGINFO"
test -s "$CHECK_ROOT/arch/.MTREE"
test -s "$CHECK_ROOT/arch/.BUILDINFO"
python3 - "$CHECK_ROOT/arch" <<'PYMTREE'
import gzip, hashlib, pathlib, shlex, sys
root = pathlib.Path(sys.argv[1])
checked = 0
for line in gzip.open(root / '.MTREE', 'rt'):
    if not line.startswith('./'):
        continue
    fields = shlex.split(line)
    digest = next((v.split('=', 1)[1] for v in fields[1:] if v.startswith('sha256digest=')), None)
    if digest:
        path = root / fields[0]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, str(path)
        checked += 1
assert checked > 0, 'MTREE contains no payload hashes'
print(f'Validated {checked} Arch MTREE hashes')
PYMTREE
# Each format must carry precisely the same application, runtime and licensing files.
for format in deb rpm arch; do
    root="$CHECK_ROOT/$format"
    test -x "$root/usr/bin/open-media-converter"
    test -s "$root/usr/share/doc/open-media-converter/copyright"
    test -s "$root/usr/share/licenses/open-media-converter/LICENSE"
    test -s "$root/usr/share/applications/open-media-converter.desktop"
    test -s "$root/usr/share/applications/org.omc.OpenMediaConverter.desktop"
    grep -qx 'NoDisplay=true' "$root/usr/share/applications/org.omc.OpenMediaConverter.desktop"
    grep -qx 'Exec=/usr/bin/open-media-converter %F' "$root/usr/share/applications/open-media-converter.desktop"
    grep -qx 'Icon=open-media-converter' "$root/usr/share/applications/open-media-converter.desktop"
    grep -qx 'StartupWMClass=open-media-converter' "$root/usr/share/applications/open-media-converter.desktop"
    test -s "$root/usr/share/metainfo/open-media-converter.metainfo.xml"
    test -s "$root/usr/share/man/man1/open-media-converter.1"
    # Native packages must not overwrite the shared system icon-theme index.
    test ! -e "$root/usr/share/icons/hicolor/index.theme"
    test -s "$root/usr/share/icons/hicolor/scalable/apps/open-media-converter.svg"
    test -s "$root/usr/share/icons/hicolor/256x256/apps/open-media-converter.png"
    (cd "$root" && find opt usr -type f -print0 | sort -z | xargs -0 sha256sum) > "$CHECK_ROOT/$format.sha256"
done
diff -u "$CHECK_ROOT/deb.sha256" "$CHECK_ROOT/rpm.sha256"
diff -u "$CHECK_ROOT/deb.sha256" "$CHECK_ROOT/arch.sha256"
APP_ROOT="$CHECK_ROOT/deb/opt/open-media-converter"
python3 - "$APP_ROOT/omc.jar" <<'PYNATIVES'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as jar:
    names = set(jar.namelist())
    for ui in ('ui/main_window.ui', 'ui/settings_dialog.ui'):
        assert ui in names, f'Missing packaged UI resource: {ui}'
    # Embedded conversion tools must be Linux x86-64 ELF binaries.
    for tool in ('ffmpeg', 'ffprobe', 'pandoc'):
        matches = [n for n in names if n.endswith('/' + tool)]
        assert matches, f'Missing embedded tool in shaded jar: {tool}'
        with jar.open(matches[0]) as binary:
            header = binary.read(20)
        assert header[:6] == b'\x7fELF\x02\x01' and int.from_bytes(header[18:20], 'little') == 62, \
            f'Embedded tool must be a Linux x86-64 ELF binary: {matches[0]}'
print('Shaded jar contains UI resources and Linux amd64 embedded tools')
PYNATIVES
"$APP_ROOT/runtime/bin/java" --list-modules > "$CHECK_ROOT/modules"
grep -q '^jdk.localedata@' "$CHECK_ROOT/modules"
grep -q '^jdk.zipfs@' "$CHECK_ROOT/modules"
# Exercise native GTK resource loading with the actual bundled JVM/JAR.
cat > "$CHECK_ROOT/PackageRuntimeCheck.java" <<'JAVA'
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.GtkBuilder;

public class PackageRuntimeCheck {
    public static void main(String[] args) throws Exception {
        if (!"amd64".equals(System.getProperty("os.arch"))) {
            throw new AssertionError("Bundled JVM must target amd64");
        }
        Class.forName("org.gnome.glib.GLib");
        Class.forName("org.gnome.glib.MainContext");
        Gtk.init();
        for (String ui : new String[]{"/ui/main_window.ui", "/ui/settings_dialog.ui"}) {
            String uiXml;
            try (var inputStream = PackageRuntimeCheck.class.getResourceAsStream(ui)) {
                if (inputStream == null) {
                    throw new AssertionError("Missing packaged UI resource: " + ui);
                }
                uiXml = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
            }
            var builder = new GtkBuilder();
            builder.addFromString(uiXml, uiXml.getBytes(StandardCharsets.UTF_8).length);
        }
        System.out.println("Packaged runtime, GTK resources and UI definitions passed");
    }
}
JAVA
javac --release 23 -cp "$APP_ROOT/omc.jar" "$CHECK_ROOT/PackageRuntimeCheck.java"
xvfb-run -a "$APP_ROOT/runtime/bin/java" --enable-native-access=ALL-UNNAMED \
    -cp "$CHECK_ROOT:$APP_ROOT/omc.jar" PackageRuntimeCheck
# Bound the real application's startup; its GTK entry point has no dedicated smoke switch.
set +e
XDG_CONFIG_HOME="$CHECK_ROOT/config" XDG_DATA_HOME="$CHECK_ROOT/data" XDG_STATE_HOME="$CHECK_ROOT/state" \
    xvfb-run -a timeout -k 10s 25s "$APP_ROOT/runtime/bin/java" --enable-native-access=ALL-UNNAMED \
    -jar "$APP_ROOT/omc.jar" > "$CHECK_ROOT/launcher.log" 2>&1
launch_status=$?
set -e
cat "$CHECK_ROOT/launcher.log"
[[ "$launch_status" == 124 ]]
if grep -Eq 'Startup failed|NoClassDefFoundError|NoSuchMethodError' "$CHECK_ROOT/launcher.log"; then
    exit 1
fi
# AppImage, when built, must extract and carry the same launcher payload.
if [ -s "$APPIMAGE" ]; then
    (cd "$CHECK_ROOT" && "$APPIMAGE" --appimage-extract >/dev/null 2>&1)
    test -x "$CHECK_ROOT/squashfs-root/AppRun"
    test -s "$CHECK_ROOT/squashfs-root/opt/open-media-converter/omc.jar"
    test -x "$CHECK_ROOT/squashfs-root/opt/open-media-converter/runtime/bin/java"
    cmp "$APP_ROOT/omc.jar" "$CHECK_ROOT/squashfs-root/opt/open-media-converter/omc.jar"
    echo 'AppImage payload matches the staged application'
fi
echo 'All package formats and the bundled launcher passed'
