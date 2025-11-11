Name:           open-media-converter
Version:        1.0.0
Release:        1%{?dist}
Summary:        Native Linux media and document converter
License:        MIT
URL:            https://github.com/albilu/open-media-converter
Source0:        %{name}-%{version}.tar.gz

BuildArch:      x86_64
%global debug_package %{nil}
# BuildRequires are not needed when JAR is pre-built by build script
# BuildRequires:  java-23-openjdk-devel
# BuildRequires:  maven >= 3.8
Requires:       java-23-openjdk-headless
Requires:       gtk4 >= 4.0.0
Requires:       glib2 >= 2.66.0
Requires:       gobject-introspection >= 1.66.0
Requires:       ffmpeg
Requires:       ImageMagick
Requires:       pandoc
Requires:       libreoffice-core
Recommends:     libreoffice-writer
Recommends:     libreoffice-calc
Recommends:     libreoffice-impress
Suggests:       ffmpeg-libs

%description
Open Media Converter is a comprehensive media and document conversion tool
for Linux desktop environments. It provides an integrated GTK 4-based
user interface and leverages powerful conversion tools (FFmpeg, Pandoc,
LibreOffice) to deliver reliable single-file and batch conversions.

Features:
 - Multi-format conversion: video, audio, image, and document formats
 - Batch processing with parallel conversions and per-file progress
 - Native GTK 4 UI with file list, presets, and settings dialog
 - Embedded and system binary support for conversion tools
 - Conversion presets and advanced format-specific options
 - State persistence: window geometry, file lists, and settings
 - Progress parsing and detailed logs with error reporting
 - Pause/Resume/Cancel controls and configurable parallelism

Supported formats include:
 - Video: MP4, MKV, AVI, MOV, WebM, FLV, WMV, MPEG, M4V
 - Audio: MP3, AAC, FLAC, WAV, OGG, Opus, M4A, WMA
 - Image: JPEG, PNG, GIF, WebP, BMP, TIFF, SVG, ICO
 - Document: PDF, DOCX, ODT, HTML, Markdown, RTF, TXT, EPUB

%prep
%setup -q

%build
# Build is handled by the build script, JAR is pre-built

%install
rm -rf %{buildroot}

# Create directory structure
install -d %{buildroot}/opt/%{name}/bin
install -d %{buildroot}/opt/%{name}/lib
install -d %{buildroot}/usr/bin
install -d %{buildroot}/usr/share/applications
install -d %{buildroot}/usr/share/icons/hicolor/16x16/apps
install -d %{buildroot}/usr/share/icons/hicolor/32x32/apps
install -d %{buildroot}/usr/share/icons/hicolor/48x48/apps
install -d %{buildroot}/usr/share/icons/hicolor/64x64/apps
install -d %{buildroot}/usr/share/icons/hicolor/128x128/apps
install -d %{buildroot}/usr/share/icons/hicolor/256x256/apps
install -d %{buildroot}/usr/share/icons/hicolor/scalable/apps

# Install JAR
install -m 644 lib/%{name}-1.0.0.jar %{buildroot}/opt/%{name}/lib/

# Install launcher script
install -m 755 bin/%{name} %{buildroot}/opt/%{name}/bin/

# Create symlink in /usr/bin
ln -sf /opt/%{name}/bin/%{name} %{buildroot}/usr/bin/%{name}

# Install desktop entry
install -m 644 share/applications/%{name}.desktop %{buildroot}/usr/share/applications/

# Install icons
install -m 644 share/icons/hicolor/16x16/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/16x16/apps/
install -m 644 share/icons/hicolor/32x32/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/32x32/apps/
install -m 644 share/icons/hicolor/48x48/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/48x48/apps/
install -m 644 share/icons/hicolor/64x64/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/64x64/apps/
install -m 644 share/icons/hicolor/128x128/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/128x128/apps/
install -m 644 share/icons/hicolor/256x256/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/256x256/apps/
install -m 644 share/icons/hicolor/scalable/apps/%{name}.svg %{buildroot}/usr/share/icons/hicolor/scalable/apps/

# Install embedded binaries if present
if [ -d bin/embedded/ffmpeg ]; then
    install -d %{buildroot}/opt/%{name}/bin/embedded/ffmpeg
    install -m 755 bin/embedded/ffmpeg/ffmpeg %{buildroot}/opt/%{name}/bin/embedded/ffmpeg/
fi

if [ -d bin/embedded/pandoc ]; then
    install -d %{buildroot}/opt/%{name}/bin/embedded/pandoc
    install -m 755 bin/embedded/pandoc/pandoc %{buildroot}/opt/%{name}/bin/embedded/pandoc/
fi

%files
/opt/%{name}/bin/%{name}
/opt/%{name}/lib/%{name}-1.0.0.jar
/usr/bin/%{name}
/usr/share/applications/%{name}.desktop
/usr/share/icons/hicolor/16x16/apps/%{name}.svg
/usr/share/icons/hicolor/32x32/apps/%{name}.svg
/usr/share/icons/hicolor/48x48/apps/%{name}.svg
/usr/share/icons/hicolor/64x64/apps/%{name}.svg
/usr/share/icons/hicolor/128x128/apps/%{name}.svg
/usr/share/icons/hicolor/256x256/apps/%{name}.svg
/usr/share/icons/hicolor/scalable/apps/%{name}.svg
%dir /opt/%{name}
%dir /opt/%{name}/bin
%dir /opt/%{name}/lib

%post
# Update desktop database
if [ -x /usr/bin/update-desktop-database ]; then
    /usr/bin/update-desktop-database -q /usr/share/applications &> /dev/null || :
fi

# Update icon cache
if [ -x /usr/bin/gtk4-update-icon-cache ]; then
    /usr/bin/gtk4-update-icon-cache -q -f /usr/share/icons/hicolor &> /dev/null || :
fi

# Update MIME database
if [ -x /usr/bin/update-mime-database ]; then
    /usr/bin/update-mime-database /usr/share/mime &> /dev/null || :
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  Open Media Converter has been successfully installed     ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "To launch the application:"
echo "  • From application menu: Open Media Converter"
echo "  • From terminal: open-media-converter"
echo ""
echo "All required dependencies have been installed:"
echo "  ✓ Java 23 (OpenJDK)"
echo "  ✓ GTK 4"
echo "  ✓ FFmpeg (video/audio conversion)"
echo "  ✓ ImageMagick (image conversion)"
echo "  ✓ Pandoc (document conversion)"
echo "  ✓ LibreOffice (office document conversion)"
echo ""

%preun
# Check for running instances before removal
if pgrep -f "open-media-converter" > /dev/null; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║  WARNING: Open Media Converter is currently running       ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Please close the application before uninstalling."
    echo "You may need to manually stop the process:"
    echo "  pkill -f open-media-converter"
    echo ""
fi

%postun
# Update desktop database after removal
if [ $1 -eq 0 ]; then
    if [ -x /usr/bin/update-desktop-database ]; then
        /usr/bin/update-desktop-database -q /usr/share/applications &> /dev/null || :
    fi
    
    # Update icon cache
    if [ -x /usr/bin/gtk4-update-icon-cache ]; then
        /usr/bin/gtk4-update-icon-cache -q -f /usr/share/icons/hicolor &> /dev/null || :
    fi
    
    # Update MIME database
    if [ -x /usr/bin/update-mime-database ]; then
        /usr/bin/update-mime-database /usr/share/mime &> /dev/null || :
    fi
fi

# On complete removal, inform about user data
if [ $1 -eq 0 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║  Open Media Converter has been removed                    ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Your personal settings and conversion history are preserved in:"
    echo "  ~/.config/open-media-converter/"
    echo "  ~/.local/share/open-media-converter/"
    echo ""
    echo "To remove all user data, run:"
    echo "  rm -rf ~/.config/open-media-converter/"
    echo "  rm -rf ~/.local/share/open-media-converter/"
    echo ""
fi

%changelog
* Thu Nov 06 2025 Open Media Converter Team <maintainer@openmediaconverter.org> - 1.0.0-1
- Initial RPM release
- Multi-format conversion support (video, audio, image, document)
- Native GTK 4 UI with file list and batch processing
- Embedded binary support for portable installations
- Conversion presets and state persistence
- Progress tracking and detailed logging
