# AppImage AppDir Structure

This document describes the directory structure that will be created by the `build-appimage.sh` script for the Open Media Converter AppImage.

## Directory Tree

```
AppDir/
├── AppRun                                    # Entry point script (from this directory)
├── open-media-converter.desktop                # Desktop entry (symlink to usr/share/applications/)
├── open-media-converter.png                    # Icon (symlink to usr/share/icons/hicolor/256x256/)
│
├── usr/
│   ├── bin/
│   │   └── open-media-converter                # Launcher script (from bin/)
│   │
│   ├── lib/
│   │   ├── open-media-converter.jar            # Application JAR (from target/)
│   │   │
│   │   ├── jre/                              # Bundled OpenJDK 23 (downloaded during build)
│   │   │   ├── bin/
│   │   │   │   └── java                      # Java executable
│   │   │   ├── lib/                          # JRE libraries
│   │   │   └── ...
│   │   │
│   │   ├── x86_64-linux-gnu/                 # Bundled GTK 4 and dependencies
│   │   │   ├── libgtk-4.so.1                 # GTK 4 library
│   │   │   ├── libglib-2.0.so.0              # GLib library
│   │   │   ├── libgobject-2.0.so.0           # GObject library
│   │   │   ├── libgio-2.0.so.0               # GIO library
│   │   │   ├── libgdk_pixbuf-2.0.so.0        # GdkPixbuf library
│   │   │   ├── libpango-1.0.so.0             # Pango library
│   │   │   ├── libcairo.so.2                 # Cairo library
│   │   │   ├── libharfbuzz.so.0              # HarfBuzz library
│   │   │   └── ...                           # Other GTK dependencies
│   │   │   └── gdk-pixbuf-2.0/               # GdkPixbuf modules
│   │   │       └── 2.10.0/
│   │   │           ├── loaders/              # Image format loaders
│   │   │           └── loaders.cache         # Loader cache file
│   │   │   └── girepository-1.0/             # GObject Introspection typelibs
│   │   │       ├── Gtk-4.0.typelib
│   │   │       ├── GLib-2.0.typelib
│   │   │       ├── GObject-2.0.typelib
│   │   │       └── ...
│   │   │
│   │   └── girepository-1.0/                 # Additional typelibs (if not in x86_64-linux-gnu/)
│   │       └── ...
│   │
│   └── share/
│       ├── applications/
│       │   └── open-media-converter.desktop    # Desktop entry file
│       │
│       ├── icons/
│       │   └── hicolor/                      # Icon theme
│       │       ├── 16x16/apps/open-media-converter.png
│       │       ├── 24x24/apps/open-media-converter.png
│       │       ├── 32x32/apps/open-media-converter.png
│       │       ├── 48x48/apps/open-media-converter.png
│       │       ├── 64x64/apps/open-media-converter.png
│       │       ├── 128x128/apps/open-media-converter.png
│       │       ├── 256x256/apps/open-media-converter.png
│       │       └── scalable/apps/open-media-converter.svg
│       │
│       └── glib-2.0/                         # GLib schemas
│           └── schemas/
│               └── ...
│
└── embedded/                                 # Embedded conversion tools
    ├── ffmpeg/
    │   ├── ffmpeg                            # FFmpeg binary (static)
    │   └── ffprobe                           # FFprobe binary (static)
    │
    └── pandoc/
        └── pandoc                            # Pandoc binary (static)
```

## File Purposes

### Root Level

-   **AppRun**: Entry point script that sets up environment variables and launches the application
-   **open-media-converter.desktop**: Symlink to desktop entry (required by AppImage spec for desktop integration)
-   **open-media-converter.png**: Symlink to main icon (required by AppImage spec)

### usr/bin/

-   **open-media-converter**: Launcher script (can be called directly, but AppRun is the main entry point)

### usr/lib/

-   **open-media-converter.jar**: The application JAR with all dependencies (created by Maven assembly plugin)
-   **jre/**: Bundled Java Runtime Environment (OpenJDK 23)
    -   Ensures consistent Java version across all systems
    -   Eliminates dependency on system Java installation
-   **x86_64-linux-gnu/**: Native libraries for GTK 4 and dependencies
    -   Bundled to ensure compatibility across different Linux distributions
    -   Includes GObject Introspection typelibs for java-gi bindings

### usr/share/

-   **applications/**: Desktop entry file for application launcher integration
-   **icons/**: Application icons in multiple resolutions for different display sizes
-   **glib-2.0/schemas/**: GSettings schemas (if needed for GTK settings)

### embedded/

-   **ffmpeg/**: Static FFmpeg and FFprobe binaries for video/audio/image conversion
-   **pandoc/**: Static Pandoc binary for document conversion
-   These tools are optional but provide a better out-of-the-box experience

## Environment Setup (by AppRun)

The AppRun script sets up the following environment:

### Java

-   `JAVA_HOME=$APPDIR/usr/lib/jre`
-   `PATH=$JAVA_HOME/bin:$PATH`

### Libraries

-   `LD_LIBRARY_PATH=$APPDIR/usr/lib:$APPDIR/usr/lib/x86_64-linux-gnu:...`

### GObject Introspection

-   `GI_TYPELIB_PATH=$APPDIR/usr/lib/girepository-1.0:$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0:...`

### GdkPixbuf

-   `GDK_PIXBUF_MODULEDIR=$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders`
-   `GDK_PIXBUF_MODULE_FILE=$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders.cache`

### XDG

-   `XDG_DATA_DIRS=$APPDIR/usr/share:...`

### GTK

-   `GTK_DATA_PREFIX=$APPDIR/usr`
-   `GTK_EXE_PREFIX=$APPDIR/usr`

### Application

-   `OMC_APP_DIR=$APPDIR`
-   `OMC_EMBEDDED_TOOLS=$APPDIR/embedded`

## Size Estimates

Approximate sizes for bundled components:

-   **JRE (OpenJDK 23)**: ~120-150 MB
-   **GTK 4 + Dependencies**: ~50-80 MB
-   **Application JAR**: ~30-50 MB (includes all Java dependencies)
-   **Embedded Tools**:
    -   FFmpeg (static): ~100-130 MB
    -   Pandoc (static): ~30-50 MB
-   **Icons and Resources**: ~5 MB

**Total AppImage Size (with all components)**: ~350-500 MB

**Note**: If embedded tools are not included, size reduces to ~200-280 MB

## Runtime Requirements

### Minimal System Requirements

-   Linux kernel 3.10+ (for AppImage FUSE support)
-   FUSE 2.x (for mounting AppImage)
-   Display server (X11 or Wayland)
-   ~500 MB disk space for AppImage file
-   ~100 MB additional space for runtime extraction (in /tmp or $APPDIR)

### Optional System Dependencies

If bundled libraries don't work, the system will fall back to:

-   GTK 4.0+ (system package)
-   GLib 2.66+ (system package)
-   GObject Introspection 1.66+ (system package)

The application will attempt to use bundled libraries first, then fall back to system libraries if needed.

## AppImage Specification Compliance

This structure follows the [AppImage specification](https://docs.appimage.org/reference/appdir.html):

1. ✅ **AppRun executable at root**: Entry point for the application
2. ✅ **Desktop file symlinked at root**: `open-media-converter.desktop` → `usr/share/applications/open-media-converter.desktop`
3. ✅ **Icon file symlinked at root**: `open-media-converter.png` → `usr/share/icons/hicolor/256x256/apps/open-media-converter.png`
4. ✅ **usr/ directory structure**: Follows FHS (Filesystem Hierarchy Standard)
5. ✅ **Self-contained**: All dependencies bundled (Java, GTK, tools)

## Build Process (overview)

The `build-appimage.sh` script (Task 88) will:

1. Create the AppDir structure
2. Copy application files (JAR, launcher, icons, desktop entry)
3. Download and extract OpenJDK 23
4. Download and extract GTK 4 libraries (from Ubuntu packages)
5. Copy embedded tools (ffmpeg, pandoc) if available
6. Set proper permissions
7. Create symlinks at root (desktop file, icon)
8. Run `appimagetool` to create the final `.AppImage` file

## Testing

To test the AppDir structure before creating AppImage:

```bash
cd AppDir
./AppRun
```

This will run the application directly from the extracted directory structure.
