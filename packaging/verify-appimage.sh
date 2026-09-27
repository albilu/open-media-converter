#!/bin/bash
# Run on a clean desktop baseline, without host Java, GTK4 or converters.
set -euo pipefail
APPIMAGE=$(realpath "${1:?Pass the AppImage to verify}")
if ldconfig -p | grep 'libgtk-4\.so' >/dev/null; then
    echo 'Portability checks require a system without GTK4' >&2
    exit 1
fi
for tool in java ffmpeg ffprobe pandoc convert magick soffice libreoffice; do
    if command -v "$tool" >/dev/null; then
        echo "Portability checks must not have host $tool" >&2
        exit 1
    fi
done
CHECK_ROOT=$(mktemp -d)
trap 'chmod -R u+w "$CHECK_ROOT"; rm -rf "$CHECK_ROOT"' EXIT
mkdir "$CHECK_ROOT/path with spaces" "$CHECK_ROOT/tmp"
cp "$APPIMAGE" "$CHECK_ROOT/app.AppImage"
chmod +x "$CHECK_ROOT/app.AppImage"
(cd "$CHECK_ROOT/path with spaces" && "$CHECK_ROOT/app.AppImage" --appimage-extract >/dev/null)
APPDIR="$CHECK_ROOT/path with spaces/squashfs-root"
test -x "$APPDIR/AppRun"
test -s "$APPDIR/usr/lib/omc/libgtk-4.so.1"
test "$(file -Lb --mime-type "$APPDIR/.DirIcon")" = image/png
test -s "$APPDIR/usr/share/metainfo/org.omc.open-media-converter.appdata.xml"
python3 "$(dirname "$0")/appimage/check-abi.py" "$APPDIR"
chmod -R a-w "$APPDIR"
export TMPDIR="$CHECK_ROOT/tmp"
export XDG_CONFIG_HOME="$CHECK_ROOT/config" XDG_DATA_HOME="$CHECK_ROOT/data"
export XDG_STATE_HOME="$CHECK_ROOT/state" XDG_CACHE_HOME="$CHECK_ROOT/cache"
export LANG=C.UTF-8 GTK_A11Y=none GDK_BACKEND=x11 G_DEBUG=fatal-criticals
unset LD_LIBRARY_PATH GDK_PIXBUF_MODULEDIR GDK_PIXBUF_MODULE_FILE
timeout -k 10s 150s xvfb-run -a -s '-screen 0 1280x900x24' dbus-run-session -- \
    python3 - "$APPDIR" "$CHECK_ROOT" <<'PY'
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import zipfile

appdir, root = map(Path, sys.argv[1:])
tools = root / 'tools'
tools.mkdir()
with zipfile.ZipFile(appdir / 'opt/open-media-converter/omc.jar') as jar:
    for name, directory in (('ffmpeg', 'ffmpeg'), ('ffprobe', 'ffmpeg'), ('pandoc', 'pandoc')):
        tool = tools / name
        tool.write_bytes(jar.read(f'bin/linux-x86_64/{directory}/{name}'))
        tool.chmod(0o755)


def run(name, *args):
    result = subprocess.run([str(tools / name), *map(str, args)],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, f'{name}: {result.stdout}\n{result.stderr}'
    return result.stdout


# Real codec work catches native dependencies hidden by --version alone.
movie = root / 'movie.mp4'
run('ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i',
    'color=c=blue:s=64x64:d=1', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', movie)
streams = json.loads(run('ffprobe', '-v', 'error', '-show_streams', '-of', 'json', movie))['streams']
assert {stream['codec_name'] for stream in streams} == {'h264', 'aac'}, streams
run('ffmpeg', '-nostdin', '-v', 'error', '-i', movie, '-vn', '-c:a', 'libmp3lame', root / 'audio.mp3')
document = root / 'input.md'
document.write_text('# Portable conversion\n\nAppImage document conversion passed.\n')
run('pandoc', document, '-o', root / 'output.docx')
with zipfile.ZipFile(root / 'output.docx') as output:
    assert b'AppImage document conversion passed.' in output.read('word/document.xml')
print('Bundled H.264/AAC, MP3, FFprobe and DOCX conversions passed', flush=True)

version = subprocess.run([str(appdir / 'AppRun'), '--version'], capture_output=True, text=True, timeout=30)
assert version.returncode == 0, version.stdout + version.stderr
assert 'Open Media Converter' in version.stdout, version.stdout
runtime = subprocess.run([str(root / 'app.AppImage'), '--appimage-extract-and-run', '--version'],
                         capture_output=True, text=True, timeout=40)
assert runtime.returncode == 0, runtime.stdout + runtime.stderr
assert 'Open Media Converter' in runtime.stdout, runtime.stdout

log = root / 'startup.log'
with log.open('w') as output:
    app = subprocess.Popen([str(appdir / 'AppRun'), str(document)], stdout=output,
                           stderr=subprocess.STDOUT, start_new_session=True)
    try:
        deadline = time.monotonic() + 30
        window = None
        while time.monotonic() < deadline:
            assert app.poll() is None, f'AppImage exited early: {app.returncode}'
            search = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', '^Open Media Converter$'],
                                    capture_output=True, text=True)
            if search.returncode == 0 and 'MainWindowJavaGi initialization complete' in log.read_text():
                window = search.stdout.splitlines()[0]
                break
            time.sleep(0.5)
        assert window, 'AppImage did not display its main window within 30 seconds'
        subprocess.run(['xdotool', 'windowfocus', '--sync', window], check=True)
        subprocess.run(['xdotool', 'key', 'ctrl+o'], check=True)
        chooser = subprocess.run(['xdotool', 'search', '--sync', '--onlyvisible', '--name',
                                  'Select Files to Convert'], capture_output=True, text=True, timeout=15)
        assert chooser.returncode == 0, 'File chooser did not open from keyboard input'
        subprocess.run(['xdotool', 'key', 'Escape'], check=True)
        time.sleep(30)
        assert app.poll() is None, f'AppImage crashed: {app.returncode}'
        children = Path(f'/proc/{app.pid}/task/{app.pid}/children').read_text().split()
        mappings = ''.join(Path(f'/proc/{child}/maps').read_text() for child in children)
        for library in ('libgtk-4.so.1', 'libglib-2.0.so.0', 'libgobject-2.0.so.0',
                        'libgio-2.0.so.0', 'libgdk_pixbuf-2.0.so.0'):
            assert str(appdir / 'usr/lib/omc' / library) in mappings, library
        for child in children:
            environment = Path(f'/proc/{child}/environ').read_bytes().split(b'\0')
            assert not any(item.startswith(b'LD_LIBRARY_PATH=') for item in environment), environment
        text = log.read_text()
        for error in ('Startup failed', 'NoClassDefFoundError', 'NoSuchMethodError',
                      'symbol lookup error', 'CRITICAL', 'ExceptionInInitializerError'):
            assert error not in text, text
        print('AppImage displayed its window, accepted keyboard input and survived 30 seconds '
              'without host Java, GTK4 or converters', flush=True)
    finally:
        if app.poll() is None:
            os.killpg(app.pid, signal.SIGTERM)
        try:
            app.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(app.pid, signal.SIGKILL)
            app.wait()
        print(log.read_text(), flush=True)
assert not list((root / 'tmp').glob('omc-appimage.*')), 'AppRun leaked its loader cache'
PY
