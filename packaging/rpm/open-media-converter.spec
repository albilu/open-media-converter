%define name open-media-converter
%define version __VERSION__
%define release 1
# Keep the man page uncompressed so the payload matches the deb/arch artifacts
%global __brp_compress %{nil}

Name:           %{name}
Version:        %{version}
Release:        %{release}%{?dist}
Summary:        Native Linux media and document converter
License:        GPL-3.0-or-later
URL:            https://github.com/albilu/open-media-converter
AutoReqProv:    no
Requires:       gtk4 >= 4.10
Requires:       glib2 >= 2.66.0
Requires:       gobject-introspection >= 1.66.0
Requires:       ImageMagick
Requires:       libreoffice-core
Recommends:     ffmpeg
Recommends:     pandoc
Recommends:     libreoffice-writer
Recommends:     libreoffice-calc
Recommends:     libreoffice-impress

%description
Open Media Converter is a comprehensive media and document conversion tool
for Linux desktop environments. It provides an integrated GTK 4-based user
interface and leverages powerful conversion tools (FFmpeg, ImageMagick,
Pandoc, LibreOffice) to deliver reliable single-file and batch conversions:
multi-format video/audio/image/document support, batch processing with
per-file progress, conversion presets, state persistence, and
pause/resume/cancel controls. Ships a bundled Java 23 runtime.

%prep
# nothing to compile — staged tree provided via %{stage}

%build
# no build step

%install
mkdir -p %{buildroot}
cp -a %{stage}/. %{buildroot}/

%files
%defattr(-,root,root,-)
/opt/open-media-converter/omc.jar
/opt/open-media-converter/runtime/*
/usr/bin/open-media-converter
/usr/share/applications/open-media-converter.desktop
/usr/share/metainfo/open-media-converter.metainfo.xml
/usr/share/man/man1/open-media-converter.1*
/usr/share/icons/hicolor/index.theme
/usr/share/icons/hicolor/scalable/apps/open-media-converter.svg
/usr/share/icons/hicolor/*x*/apps/open-media-converter.*
%doc /usr/share/doc/open-media-converter/copyright
%license /usr/share/licenses/open-media-converter/LICENSE

%post
update-desktop-database -q || :
update-mime-database /usr/share/mime >/dev/null 2>&1 || :
gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || :

%postun
update-desktop-database -q || :
