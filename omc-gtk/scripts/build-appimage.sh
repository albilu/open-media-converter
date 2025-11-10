#!/usr/bin/env bash
###############################################################################
# Open Media Converter - AppImage Build Script
#
# This script builds a self-contained AppImage for Open Media Converter with
# bundled Java runtime, GTK 4 libraries, and optional embedded tools.
#
# Prerequisites:
#   - Maven 3.8+ (for building JAR)
#   - appimagetool (https://github.com/AppImage/AppImageKit/releases)
#   - wget or curl (for downloading dependencies)
#   - tar, gzip (for extracting archives)
#
# Usage: ./scripts/build-appimage.sh [OPTIONS]
#
# Options:
#   --skip-build         Skip Maven build (use existing JAR)
#   --no-embedded-tools  Skip embedding ffmpeg/pandoc (smaller package)
#   --no-gtk-bundle      Use system GTK instead of bundling (not recommended)
#   --arch ARCH          Target architecture (x86_64 or aarch64, default: x86_64)
#   --help               Show this help message
#
# Output: dist/Open_Media_Converter-1.0.0-x86_64.AppImage
#
# The resulting AppImage will be:
#   - Fully self-contained with Java 23 runtime
#   - Portable across all modern Linux distributions
#   - ~350-500 MB with embedded tools, ~200-280 MB without
#
###############################################################################

set -e  # Exit on error

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OMC_GTK_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$OMC_GTK_ROOT")"
BUILD_DIR="${PROJECT_ROOT}/build/appimage"
APPDIR="${BUILD_DIR}/AppDir"
DIST_DIR="${PROJECT_ROOT}/dist"
PACKAGING_DIR="${OMC_GTK_ROOT}/packaging/appimage"
DOWNLOAD_DIR="${BUILD_DIR}/downloads"

# Auto-detect version from POM file
detect_version() {
    local pom_file="${OMC_GTK_ROOT}/pom.xml"
    if [ ! -f "$pom_file" ]; then
        pom_file="${PROJECT_ROOT}/pom.xml"
    fi
    if [ -f "$pom_file" ]; then
        # Extract version from pom.xml (handles SNAPSHOT versions)
        local version=$(grep -m 1 "<version>" "$pom_file" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')
        if [ -n "$version" ]; then
            echo "$version"
            return 0
        fi
    fi
    # Fallback to hardcoded version if detection fails
    echo "1.0.0-SNAPSHOT"
}

# Application metadata
APP_NAME="open-media-converter"
APP_SNAPSHOT_VERSION="$(detect_version)"
APP_VERSION="${APP_SNAPSHOT_VERSION%-SNAPSHOT}"  # Remove -SNAPSHOT suffix for package version
ARCH="x86_64"
APPIMAGE_FILENAME="Open_Media_Converter-${APP_VERSION}-${ARCH}.AppImage"

# Download URLs (using well-known stable sources)
JRE_URL="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_x64_linux_hotspot_23.0.1_11.tar.gz"
APPIMAGETOOL_URL="https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Parse command-line options
SKIP_BUILD=false
NO_EMBEDDED_TOOLS=false
NO_GTK_BUNDLE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --no-embedded-tools)
            NO_EMBEDDED_TOOLS=true
            shift
            ;;
        --no-gtk-bundle)
            NO_GTK_BUNDLE=true
            shift
            ;;
        --arch)
            ARCH="$2"
            shift 2
            ;;
        --help)
            grep "^#" "$0" | grep -v "^#!/" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown option: $1${NC}" >&2
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Update filenames based on architecture
APPIMAGE_FILENAME="Open_Media_Converter-${APP_VERSION}-${ARCH}.AppImage"

# Logging functions
log_info() {
    echo -e "${GREEN}▶${NC} $1"
}

log_step() {
    echo -e "${BLUE}━━━ $1${NC}"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1" >&2
}

log_download() {
    echo -e "${CYAN}↓${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites"
    
    local missing=false
    
    # Check for wget or curl
    if ! command -v wget &> /dev/null && ! command -v curl &> /dev/null; then
        log_error "Neither wget nor curl found. Install one with: sudo apt install wget"
        missing=true
    fi
    
    # Check for tar
    if ! command -v tar &> /dev/null; then
        log_error "tar not found. Install with: sudo apt install tar"
        missing=true
    fi
    
    # Check for Maven (if not skipping build)
    if [ "$SKIP_BUILD" = false ] && ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Install with: sudo apt install maven"
        missing=true
    fi
    
    # Check for appimagetool
    if ! command -v appimagetool &> /dev/null && [ ! -x "${DOWNLOAD_DIR}/appimagetool" ]; then
        log_warn "appimagetool not found in PATH (will download)"
    fi
    
    if [ "$missing" = true ]; then
        exit 1
    fi
    
    log_success "All required tools available"
}

# Download a file with progress
download_file() {
    local url="$1"
    local output="$2"
    local desc="$3"
    
    log_download "Downloading $desc..."
    
    if command -v wget &> /dev/null; then
        wget --progress=bar:force -O "$output" "$url" 2>&1 | \
            grep -oP '\d+%' | uniq | while read -r percent; do
                echo -ne "\r  Progress: $percent"
            done
        echo ""
    elif command -v curl &> /dev/null; then
        curl -L -o "$output" --progress-bar "$url"
    else
        log_error "No download tool available"
        exit 1
    fi
    
    log_success "Downloaded $desc"
}

# Build JAR with Maven
build_jar() {
    if [ "$SKIP_BUILD" = true ]; then
        log_step "Skipping Maven build (--skip-build)"
        
        # Check if JAR exists
        if [ ! -f "${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar" ]; then
            log_error "JAR not found: ${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
            log_error "Run without --skip-build to build JAR first"
            exit 1
        fi
    else
        log_step "Building JAR with Maven"
        cd "$PROJECT_ROOT"
        mvn clean package -DskipTests
        log_success "JAR built successfully"
    fi
}

# Clean and create build directory
prepare_build_directory() {
    log_step "Preparing build directory"
    
    # Clean previous build
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    mkdir -p "$APPDIR"
    mkdir -p "$DOWNLOAD_DIR"
    mkdir -p "$DIST_DIR"
    
    # Create AppDir structure following FHS
    mkdir -p "$APPDIR/usr/bin"
    mkdir -p "$APPDIR/usr/lib"
    mkdir -p "$APPDIR/usr/share/applications"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/16x16/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/24x24/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/32x32/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/48x48/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/64x64/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/128x128/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
    mkdir -p "$APPDIR/usr/share/icons/hicolor/scalable/apps"
    
    if [ "$NO_EMBEDDED_TOOLS" = false ]; then
        mkdir -p "$APPDIR/embedded/ffmpeg"
        mkdir -p "$APPDIR/embedded/pandoc"
    fi
    
    log_success "Build directory prepared"
}

# Download and extract OpenJDK 23 JRE
download_jre() {
    log_step "Downloading OpenJDK 23 JRE"
    
    local jre_archive="${DOWNLOAD_DIR}/openjdk-23-jre.tar.gz"
    
    # Check if already downloaded
    if [ -f "$jre_archive" ]; then
        log_info "JRE archive already exists, skipping download"
    else
        download_file "$JRE_URL" "$jre_archive" "OpenJDK 23 JRE"
    fi
    
    log_info "Extracting JRE..."
    tar -xzf "$jre_archive" -C "${DOWNLOAD_DIR}/"
    
    # Find the extracted JRE directory (it will be named jdk-23.0.1+11-jre or similar)
    local jre_dir=$(find "${DOWNLOAD_DIR}" -maxdepth 1 -type d -name "*jre" | head -1)
    
    if [ -z "$jre_dir" ] || [ ! -d "$jre_dir" ]; then
        log_error "Failed to find extracted JRE directory"
        exit 1
    fi
    
    # Copy to AppDir
    log_info "Copying JRE to AppDir..."
    cp -r "$jre_dir" "$APPDIR/usr/lib/jre"
    
    # Remove unnecessary files to reduce size
    log_info "Optimizing JRE size..."
    rm -rf "$APPDIR/usr/lib/jre/man"
    rm -rf "$APPDIR/usr/lib/jre/legal"
    find "$APPDIR/usr/lib/jre" -name "*.debuginfo" -delete
    
    local jre_size=$(du -sh "$APPDIR/usr/lib/jre" | cut -f1)
    log_success "JRE installed (${jre_size})"
}

# Download GTK 4 libraries from system or packages
bundle_gtk_libraries() {
    if [ "$NO_GTK_BUNDLE" = true ]; then
        log_step "Skipping GTK library bundling (--no-gtk-bundle)"
        log_warn "AppImage will require GTK 4 to be installed on target system"
        return
    fi
    
    log_step "Bundling GTK 4 libraries"
    
    # Create library directories
    mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu"
    mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0"
    mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders"
    
    # List of critical GTK 4 libraries to bundle
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
    
    log_info "Copying GTK libraries from system..."
    local copied=0
    local missing=0
    
    for lib in "${gtk_libs[@]}"; do
        # Find library on system
        local lib_path=$(ldconfig -p | grep "$lib" | grep x86-64 | awk '{print $NF}' | head -1)
        
        if [ -n "$lib_path" ] && [ -f "$lib_path" ]; then
            cp -P "$lib_path" "$APPDIR/usr/lib/x86_64-linux-gnu/"
            # Also copy any symlinks
            local lib_dir=$(dirname "$lib_path")
            local lib_base=$(basename "$lib" | cut -d. -f1,2,3)
            cp -P "$lib_dir/$lib_base"* "$APPDIR/usr/lib/x86_64-linux-gnu/" 2>/dev/null || true
            copied=$((copied + 1))
        else
            log_warn "  Library not found: $lib (AppImage may need system version)"
            missing=$((missing + 1))
        fi
    done
    
    log_info "Bundled $copied GTK libraries ($missing missing, will use system fallback)"
    
    # Copy GObject Introspection typelibs
    log_info "Copying GObject Introspection typelibs..."
    local typelib_dir="/usr/lib/x86_64-linux-gnu/girepository-1.0"
    if [ -d "$typelib_dir" ]; then
        cp "$typelib_dir/Gtk-4.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/GLib-2.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/GObject-2.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/Gio-2.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/Gdk-4.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/GdkPixbuf-2.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/Pango-1.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/cairo-1.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/HarfBuzz-0.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        cp "$typelib_dir/Graphene-1.0.typelib" "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0/" 2>/dev/null || true
        local typelib_count=$(ls -1 "$APPDIR/usr/lib/x86_64-linux-gnu/girepository-1.0" | wc -l)
        log_info "  Copied $typelib_count typelibs"
    else
        log_warn "  Typelib directory not found: $typelib_dir"
    fi
    
    # Copy GdkPixbuf loaders
    log_info "Copying GdkPixbuf loaders..."
    local pixbuf_loaders="/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders"
    if [ -d "$pixbuf_loaders" ]; then
        cp -r "$pixbuf_loaders"/* "$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders/" 2>/dev/null || true
        # Update loader cache to use AppDir paths
        if command -v gdk-pixbuf-query-loaders &> /dev/null; then
            GDK_PIXBUF_MODULEDIR="$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders" \
                gdk-pixbuf-query-loaders > "$APPDIR/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders.cache" 2>/dev/null || true
        fi
    fi
    
    local gtk_size=$(du -sh "$APPDIR/usr/lib/x86_64-linux-gnu" | cut -f1)
    log_success "GTK libraries bundled (${gtk_size})"
}

# Copy application JAR
copy_jar() {
    log_step "Copying application JAR"
    
    local jar_source="${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
    local jar_dest="$APPDIR/usr/lib/${APP_NAME}.jar"
    
    if [ ! -f "$jar_source" ]; then
        log_error "JAR not found: $jar_source"
        exit 1
    fi
    
    cp "$jar_source" "$jar_dest"
    chmod 644 "$jar_dest"
    
    local jar_size=$(du -h "$jar_dest" | cut -f1)
    log_success "JAR copied (${jar_size})"
}

# Copy launcher script (not used as main entry, but included for completeness)
copy_launcher() {
    log_step "Copying launcher script"
    
    local launcher_source="${OMC_GTK_ROOT}/bin/${APP_NAME}"
    local launcher_dest="$APPDIR/usr/bin/${APP_NAME}"
    
    if [ ! -f "$launcher_source" ]; then
        log_error "Launcher script not found: $launcher_source"
        exit 1
    fi
    
    cp "$launcher_source" "$launcher_dest"
    chmod 755 "$launcher_dest"
    
    log_success "Launcher script copied"
}

# Copy AppRun entry point
copy_apprun() {
    log_step "Copying AppRun entry point"
    
    local apprun_source="${PACKAGING_DIR}/AppRun"
    local apprun_dest="$APPDIR/AppRun"
    
    if [ ! -f "$apprun_source" ]; then
        log_error "AppRun not found: $apprun_source"
        exit 1
    fi
    
    cp "$apprun_source" "$apprun_dest"
    chmod 755 "$apprun_dest"
    
    log_success "AppRun copied"
}

# Copy desktop entry
copy_desktop_entry() {
    log_step "Copying desktop entry"
    
    local desktop_source="${OMC_GTK_ROOT}/packaging/deb/usr/share/applications/${APP_NAME}.desktop"
    local desktop_dest="$APPDIR/usr/share/applications/${APP_NAME}.desktop"
    
    if [ ! -f "$desktop_source" ]; then
        log_error "Desktop entry not found: $desktop_source"
        exit 1
    fi
    
    cp "$desktop_source" "$desktop_dest"
    chmod 644 "$desktop_dest"
    
    # Create symlink at AppDir root (required by AppImage spec)
    ln -sf "usr/share/applications/${APP_NAME}.desktop" "$APPDIR/${APP_NAME}.desktop"
    
    log_success "Desktop entry copied and symlinked"
}

# Copy icons
copy_icons() {
    log_step "Copying icons"
    
    local icons_source="${OMC_GTK_ROOT}/src/main/resources/icons/hicolor"
    local icons_dest="$APPDIR/usr/share/icons/hicolor"
    
    if [ ! -d "$icons_source" ]; then
        log_error "Icons directory not found: $icons_source"
        exit 1
    fi
    
    local icon_count=0
    
    # Copy PNG icons
    for size in 16x16 24x24 32x32 48x48 64x64 128x128 256x256; do
        local icon_file="${icons_source}/${size}/apps/${APP_NAME}.png"
        if [ -f "$icon_file" ]; then
            cp "$icon_file" "${icons_dest}/${size}/apps/"
            chmod 644 "${icons_dest}/${size}/apps/${APP_NAME}.png"
            icon_count=$((icon_count + 1))
        fi
    done
    
    # Copy SVG icon
    local svg_icon="${icons_source}/scalable/apps/${APP_NAME}.svg"
    if [ -f "$svg_icon" ]; then
        cp "$svg_icon" "${icons_dest}/scalable/apps/"
        chmod 644 "${icons_dest}/scalable/apps/${APP_NAME}.svg"
        icon_count=$((icon_count + 1))
    fi
    
    # Create icon symlink at AppDir root (required by AppImage spec)
    ln -sf "usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png" "$APPDIR/${APP_NAME}.png"
    
    log_success "Icons copied (${icon_count} files) and symlinked"
}

# Copy embedded conversion tools
copy_embedded_tools() {
    if [ "$NO_EMBEDDED_TOOLS" = true ]; then
        log_step "Skipping embedded tools (--no-embedded-tools)"
        log_warn "AppImage will require ffmpeg and pandoc to be installed on target system"
        return
    fi
    
    log_step "Copying embedded conversion tools"
    
    local binaries_source="${OMC_GTK_ROOT}/src/main/resources/bin"
    
    if [ ! -d "$binaries_source" ]; then
        log_warn "No embedded binaries directory found"
        return
    fi
    
    local arch_dir="${binaries_source}/linux-${ARCH}"
    if [ ! -d "$arch_dir" ]; then
        log_warn "No binaries found for architecture: ${ARCH}"
        return
    fi
    
    local tools_copied=0
    
    # Copy ffmpeg
    if [ -f "${arch_dir}/ffmpeg/ffmpeg" ]; then
        cp "${arch_dir}/ffmpeg/ffmpeg" "$APPDIR/embedded/ffmpeg/"
        chmod 755 "$APPDIR/embedded/ffmpeg/ffmpeg"
        local ffmpeg_size=$(du -h "$APPDIR/embedded/ffmpeg/ffmpeg" | cut -f1)
        log_info "  Copied ffmpeg (${ffmpeg_size})"
        tools_copied=$((tools_copied + 1))
    fi
    
    # Copy ffprobe if present
    if [ -f "${arch_dir}/ffmpeg/ffprobe" ]; then
        cp "${arch_dir}/ffmpeg/ffprobe" "$APPDIR/embedded/ffmpeg/"
        chmod 755 "$APPDIR/embedded/ffmpeg/ffprobe"
        log_info "  Copied ffprobe"
        tools_copied=$((tools_copied + 1))
    fi
    
    # Copy pandoc
    if [ -f "${arch_dir}/pandoc/pandoc" ]; then
        cp "${arch_dir}/pandoc/pandoc" "$APPDIR/embedded/pandoc/"
        chmod 755 "$APPDIR/embedded/pandoc/pandoc"
        local pandoc_size=$(du -h "$APPDIR/embedded/pandoc/pandoc" | cut -f1)
        log_info "  Copied pandoc (${pandoc_size})"
        tools_copied=$((tools_copied + 1))
    fi
    
    if [ $tools_copied -eq 0 ]; then
        log_warn "No embedded tools found to copy"
    else
        log_success "Embedded tools copied (${tools_copied} binaries)"
    fi
}

# Set file permissions
set_permissions() {
    log_step "Setting file permissions"
    
    # Set directory permissions
    find "$APPDIR" -type d -exec chmod 755 {} \;
    
    # Set regular file permissions
    find "$APPDIR" -type f -exec chmod 644 {} \;
    
    # Set executable permissions for binaries and scripts
    chmod 755 "$APPDIR/AppRun"
    chmod 755 "$APPDIR/usr/bin/${APP_NAME}" 2>/dev/null || true
    find "$APPDIR/usr/lib/jre/bin" -type f -exec chmod 755 {} \; 2>/dev/null || true
    find "$APPDIR/embedded" -type f -exec chmod 755 {} \; 2>/dev/null || true
    
    log_success "Permissions set"
}

# Calculate AppDir size
calculate_appdir_size() {
    log_step "Calculating AppDir size"
    
    local size=$(du -sh "$APPDIR" | cut -f1)
    log_success "AppDir size: $size"
}

# Download appimagetool if not available
ensure_appimagetool() {
    log_step "Checking for appimagetool"
    
    if command -v appimagetool &> /dev/null; then
        log_success "appimagetool found in PATH"
        return
    fi
    
    local appimagetool_path="${DOWNLOAD_DIR}/appimagetool"
    
    if [ -x "$appimagetool_path" ]; then
        log_success "appimagetool found in downloads"
        return
    fi
    
    log_info "Downloading appimagetool..."
    download_file "$APPIMAGETOOL_URL" "$appimagetool_path" "appimagetool"
    chmod +x "$appimagetool_path"
    
    log_success "appimagetool ready"
}

# Build AppImage with appimagetool
build_appimage() {
    log_step "Building AppImage"
    
    ensure_appimagetool
    
    # Determine appimagetool command
    local appimagetool_cmd
    if command -v appimagetool &> /dev/null; then
        appimagetool_cmd="appimagetool"
    else
        appimagetool_cmd="${DOWNLOAD_DIR}/appimagetool"
    fi
    
    # Build AppImage
    log_info "Running appimagetool..."
    
    # Capture output to a temp file to check exit status properly
    local appimagetool_log="${BUILD_DIR}/appimagetool.log"
    
    # Use --appimage-extract-and-run if FUSE is not available (common in Docker)
    local extract_flag=""
    if [ ! -e /dev/fuse ] && [[ "$appimagetool_cmd" == *"/appimagetool" ]]; then
        log_info "FUSE not available, using --appimage-extract-and-run mode..."
        extract_flag="--appimage-extract-and-run"
    fi
    
    if ARCH="${ARCH}" "$appimagetool_cmd" $extract_flag "$APPDIR" "${DIST_DIR}/${APPIMAGE_FILENAME}" > "$appimagetool_log" 2>&1; then
        # Show warnings/errors if any
        if grep -iE "ERROR|WARNING" "$appimagetool_log" > /dev/null 2>&1; then
            grep -iE "ERROR|WARNING" "$appimagetool_log" | while read -r line; do
                echo "  $line"
            done
        fi
    else
        log_error "appimagetool execution failed with exit code $?"
        echo "  Full output:"
        cat "$appimagetool_log" | sed 's/^/    /'
        exit 1
    fi
    
    if [ ! -f "${DIST_DIR}/${APPIMAGE_FILENAME}" ]; then
        log_error "AppImage file was not created"
        exit 1
    fi
    
    # Make AppImage executable
    chmod +x "${DIST_DIR}/${APPIMAGE_FILENAME}"
    
    local appimage_size=$(du -h "${DIST_DIR}/${APPIMAGE_FILENAME}" | cut -f1)
    log_success "AppImage built: ${APPIMAGE_FILENAME} (${appimage_size})"
}

# Test AppImage (basic validation)
test_appimage() {
    log_step "Testing AppImage"
    
    local appimage_path="${DIST_DIR}/${APPIMAGE_FILENAME}"
    
    # Test --help flag
    log_info "Testing --help flag..."
    if "$appimage_path" --help &>/dev/null; then
        log_success "Help flag works"
    else
        log_warn "Help flag test failed (may not be implemented)"
    fi
    
    # Test extraction
    log_info "Testing AppImage extraction..."
    local test_extract="${BUILD_DIR}/test_extract"
    mkdir -p "$test_extract"
    cd "$test_extract"
    "$appimage_path" --appimage-extract &>/dev/null
    
    if [ -d "squashfs-root" ] && [ -f "squashfs-root/AppRun" ]; then
        log_success "AppImage extraction works"
        rm -rf "squashfs-root"
    else
        log_warn "AppImage extraction test failed"
    fi
    
    cd "$PROJECT_ROOT"
    
    log_success "AppImage validation complete"
}

# Show package information
show_package_info() {
    log_step "Package Information"
    
    echo ""
    echo -e "${CYAN}Application:${NC}   Open Media Converter"
    echo -e "${CYAN}Version:${NC}       ${APP_VERSION}"
    echo -e "${CYAN}Architecture:${NC}  ${ARCH}"
    echo -e "${CYAN}Filename:${NC}      ${APPIMAGE_FILENAME}"
    echo -e "${CYAN}Location:${NC}      ${DIST_DIR}/${APPIMAGE_FILENAME}"
    
    local appimage_size=$(du -h "${DIST_DIR}/${APPIMAGE_FILENAME}" | cut -f1)
    echo -e "${CYAN}Size:${NC}          ${appimage_size}"
    
    echo ""
    echo -e "${CYAN}Usage:${NC}"
    echo "  chmod +x ${DIST_DIR}/${APPIMAGE_FILENAME}"
    echo "  ./${APPIMAGE_FILENAME}"
    echo "  ./${APPIMAGE_FILENAME} file1.mp4 file2.mkv"
    echo ""
    echo -e "${CYAN}Debug Mode:${NC}"
    echo "  OMC_DEBUG=1 ./${APPIMAGE_FILENAME}"
    echo ""
    echo -e "${CYAN}Extract:${NC}"
    echo "  ./${APPIMAGE_FILENAME} --appimage-extract"
    echo ""
    echo -e "${CYAN}Desktop Integration:${NC}"
    echo "  Install appimaged for automatic desktop integration:"
    echo "  https://github.com/probonopd/go-appimage"
    echo ""
}

# Main build process
main() {
    echo ""
    echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${MAGENTA}║${NC}  ${GREEN}Open Media Converter - AppImage Builder${NC}                 ${MAGENTA}║${NC}"
    echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    check_prerequisites
    build_jar
    prepare_build_directory
    download_jre
    bundle_gtk_libraries
    copy_jar
    copy_launcher
    copy_apprun
    copy_desktop_entry
    copy_icons
    copy_embedded_tools
    set_permissions
    calculate_appdir_size
    build_appimage
    test_appimage
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}  ${GREEN}✓ AppImage Built Successfully${NC}                          ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    show_package_info
}

# Run main function
main "$@"
