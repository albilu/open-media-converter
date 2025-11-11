#!/usr/bin/env bash
###############################################################################
# Open Media Converter - Debian Package Build Script
#
# This script builds a .deb package for Open Media Converter with proper
# file layout, permissions, and dependencies.
#
# Prerequisites:
#   - Maven 3.8+ (for building JAR)
#   - dpkg-deb (for creating .deb package)
#   - lintian (optional, for package validation)
#
# Usage: ./scripts/build-deb.sh [OPTIONS]
#
# Options:
#   --skip-build    Skip Maven build (use existing JAR)
#   --no-validate   Skip lintian validation
#   --help          Show this help message
#
# Output: dist/open-media-converter_1.0.0_amd64.deb
###############################################################################

set -e  # Exit on error

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OMC_GTK_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$OMC_GTK_ROOT")"
BUILD_DIR="${PROJECT_ROOT}/build/deb"
DIST_DIR="${PROJECT_ROOT}/dist"
PACKAGING_DIR="${OMC_GTK_ROOT}/packaging/deb"

# Auto-detect version from POM file
detect_version() {
    # Try omc-gtk module pom.xml first, then fall back to root pom.xml
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
ARCH="amd64"
DEB_FILENAME="${APP_NAME}_${APP_VERSION}_${ARCH}.deb"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse command-line options
SKIP_BUILD=false
NO_VALIDATE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --no-validate)
            NO_VALIDATE=true
            shift
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

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites"
    
    local missing=false
    
    if ! command -v dpkg-deb &> /dev/null; then
        log_error "dpkg-deb not found. Install with: sudo apt install dpkg-dev"
        missing=true
    fi
    
    if [ "$SKIP_BUILD" = false ] && ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Install with: sudo apt install maven"
        missing=true
    fi
    
    if [ "$NO_VALIDATE" = false ] && ! command -v lintian &> /dev/null; then
        log_warn "lintian not found (optional). Install with: sudo apt install lintian"
    fi
    
    if [ "$missing" = true ]; then
        exit 1
    fi
    
    log_success "All required tools available"
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
    
    # Create directory structure
    mkdir -p "$BUILD_DIR/DEBIAN"
    mkdir -p "$BUILD_DIR/opt/${APP_NAME}/bin"
    mkdir -p "$BUILD_DIR/opt/${APP_NAME}/lib"
    mkdir -p "$BUILD_DIR/usr/bin"
    mkdir -p "$BUILD_DIR/usr/share/applications"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/16x16/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/32x32/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/48x48/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/64x64/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/128x128/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/256x256/apps"
    mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/scalable/apps"
    
    log_success "Build directory prepared"
}

# Copy DEBIAN control files
copy_control_files() {
    log_step "Copying DEBIAN control files"
    
    # Copy control scripts
    cp "${PACKAGING_DIR}/DEBIAN/control" "$BUILD_DIR/DEBIAN/"
    cp "${PACKAGING_DIR}/DEBIAN/postinst" "$BUILD_DIR/DEBIAN/"
    cp "${PACKAGING_DIR}/DEBIAN/prerm" "$BUILD_DIR/DEBIAN/"
    cp "${PACKAGING_DIR}/DEBIAN/postrm" "$BUILD_DIR/DEBIAN/"
    
    # Update version in control file dynamically
    sed -i "s/^Version:.*/Version: ${APP_VERSION}/" "$BUILD_DIR/DEBIAN/control"
    
    # Set executable permissions on maintainer scripts
    chmod 755 "$BUILD_DIR/DEBIAN/postinst"
    chmod 755 "$BUILD_DIR/DEBIAN/prerm"
    chmod 755 "$BUILD_DIR/DEBIAN/postrm"
    chmod 644 "$BUILD_DIR/DEBIAN/control"
    
    log_success "Control files copied (version: ${APP_VERSION})"
}

# Copy application JAR
copy_jar() {
    log_step "Copying application JAR"
    
    local jar_source="${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
    local jar_dest="$BUILD_DIR/opt/${APP_NAME}/lib/${APP_NAME}-${APP_VERSION}.jar"
    
    if [ ! -f "$jar_source" ]; then
        log_error "JAR not found: $jar_source"
        exit 1
    fi
    
    cp "$jar_source" "$jar_dest"
    chmod 644 "$jar_dest"
    
    local jar_size=$(du -h "$jar_dest" | cut -f1)
    log_success "JAR copied (${jar_size})"
}

# Copy launcher script
copy_launcher() {
    log_step "Copying launcher script"
    
    local launcher_source="${OMC_GTK_ROOT}/bin/${APP_NAME}"
    local launcher_dest="$BUILD_DIR/opt/${APP_NAME}/bin/${APP_NAME}"
    
    if [ ! -f "$launcher_source" ]; then
        log_error "Launcher script not found: $launcher_source"
        exit 1
    fi
    
    # Copy launcher to /opt/app/bin/
    cp "$launcher_source" "$launcher_dest"
    chmod 755 "$launcher_dest"
    
    # Create symlink in /usr/bin/
    ln -sf "/opt/${APP_NAME}/bin/${APP_NAME}" "$BUILD_DIR/usr/bin/${APP_NAME}"
    
    log_success "Launcher script copied"
}

# Copy desktop entry
copy_desktop_entry() {
    log_step "Copying desktop entry"
    
    local desktop_source="${PACKAGING_DIR}/usr/share/applications/${APP_NAME}.desktop"
    local desktop_dest="$BUILD_DIR/usr/share/applications/${APP_NAME}.desktop"
    
    if [ ! -f "$desktop_source" ]; then
        log_error "Desktop entry not found: $desktop_source"
        exit 1
    fi
    
    cp "$desktop_source" "$desktop_dest"
    chmod 644 "$desktop_dest"
    
    log_success "Desktop entry copied"
}

# Copy icons
copy_icons() {
    log_step "Copying icons"
    
    local icons_source="${OMC_GTK_ROOT}/src/main/resources/icons/hicolor"
    local icons_dest="$BUILD_DIR/usr/share/icons/hicolor"
    
    if [ ! -d "$icons_source" ]; then
        log_error "Icons directory not found: $icons_source"
        exit 1
    fi
    
    local icon_count=0
    
    # Copy PNG icons
    for size in 16x16 32x32 48x48 64x64 128x128 256x256; do
        local icon_file="${icons_source}/${size}/apps/${APP_NAME}.svg"
        if [ -f "$icon_file" ]; then
            cp "$icon_file" "${icons_dest}/${size}/apps/"
            chmod 644 "${icons_dest}/${size}/apps/${APP_NAME}.svg"
            icon_count=$((icon_count + 1))
        else
            log_warn "Icon not found: ${size}/apps/${APP_NAME}.svg"
        fi
    done
    
    # Copy SVG icon
    local svg_icon="${icons_source}/scalable/apps/${APP_NAME}.svg"
    if [ -f "$svg_icon" ]; then
        cp "$svg_icon" "${icons_dest}/scalable/apps/"
        chmod 644 "${icons_dest}/scalable/apps/${APP_NAME}.svg"
        icon_count=$((icon_count + 1))
    else
        log_warn "SVG icon not found: scalable/apps/${APP_NAME}.svg"
    fi
    
    log_success "Icons copied (${icon_count} files)"
}

# Copy embedded binaries (if present)
copy_embedded_binaries() {
    log_step "Copying embedded binaries (if present)"
    
    local binaries_source="${OMC_GTK_ROOT}/src/main/resources/bin"
    local binaries_dest="$BUILD_DIR/opt/${APP_NAME}/bin/embedded"
    
    if [ ! -d "$binaries_source" ]; then
        log_warn "No embedded binaries directory found (optional)"
        return
    fi
    
    # Check for Linux x86_64 binaries
    local arch_dir="${binaries_source}/linux-x86_64"
    if [ -d "$arch_dir" ]; then
        mkdir -p "$binaries_dest"
        
        # Copy ffmpeg if present
        if [ -f "${arch_dir}/ffmpeg/ffmpeg" ]; then
            mkdir -p "${binaries_dest}/ffmpeg"
            cp "${arch_dir}/ffmpeg/ffmpeg" "${binaries_dest}/ffmpeg/"
            chmod 755 "${binaries_dest}/ffmpeg/ffmpeg"
            log_info "  Copied ffmpeg binary"
        fi
        
        # Copy pandoc if present
        if [ -f "${arch_dir}/pandoc/pandoc" ]; then
            mkdir -p "${binaries_dest}/pandoc"
            cp "${arch_dir}/pandoc/pandoc" "${binaries_dest}/pandoc/"
            chmod 755 "${binaries_dest}/pandoc/pandoc"
            log_info "  Copied pandoc binary"
        fi
        
        log_success "Embedded binaries copied"
    else
        log_warn "No x86_64 binaries found (will use system tools)"
    fi
}

# Set file permissions
set_permissions() {
    log_step "Setting file permissions"
    
    # All regular files should be readable
    find "$BUILD_DIR" -type f -exec chmod 644 {} \;
    
    # Executables
    find "$BUILD_DIR/opt/${APP_NAME}/bin" -type f -exec chmod 755 {} \;
    find "$BUILD_DIR/DEBIAN" -type f -name "postinst" -o -name "prerm" -o -name "postrm" | xargs chmod 755
    
    # Directories
    find "$BUILD_DIR" -type d -exec chmod 755 {} \;
    
    log_success "Permissions set"
}

# Calculate installed size
calculate_installed_size() {
    log_step "Calculating installed size"
    
    local size_kb=$(du -sk "$BUILD_DIR" | cut -f1)
    local size_mb=$(echo "scale=2; $size_kb/1024" | bc)
    
    # Update control file with accurate installed size
    sed -i "s/^Installed-Size:.*/Installed-Size: ${size_kb}/" "$BUILD_DIR/DEBIAN/control"
    
    log_success "Installed size: ${size_mb} MB (${size_kb} KB)"
}

# Build .deb package
build_deb_package() {
    log_step "Building .deb package"
    
    mkdir -p "$DIST_DIR"
    
    # Build package
    dpkg-deb --build --root-owner-group "$BUILD_DIR" "${DIST_DIR}/${DEB_FILENAME}"
    
    local deb_size=$(du -h "${DIST_DIR}/${DEB_FILENAME}" | cut -f1)
    log_success "Package built: ${DEB_FILENAME} (${deb_size})"
}

# Validate package with lintian
validate_package() {
    if [ "$NO_VALIDATE" = true ]; then
        log_step "Skipping package validation (--no-validate)"
        return
    fi
    
    if ! command -v lintian &> /dev/null; then
        log_warn "Skipping validation (lintian not installed)"
        return
    fi
    
    log_step "Validating package with lintian"
    
    # Run lintian with reduced verbosity (only show warnings and errors)
    if lintian --no-tag-display-limit "${DIST_DIR}/${DEB_FILENAME}" 2>&1 | tee /tmp/lintian-output.txt; then
        log_success "Package validation passed"
    else
        log_warn "Package has lintian warnings (see above)"
        log_info "Note: Some warnings are expected for custom packages"
    fi
}

# Show package information
show_package_info() {
    log_step "Package Information"
    
    echo -e "${CYAN}Package:${NC}       ${APP_NAME}"
    echo -e "${CYAN}Version:${NC}       ${APP_VERSION}"
    echo -e "${CYAN}Architecture:${NC}  ${ARCH}"
    echo -e "${CYAN}Filename:${NC}      ${DEB_FILENAME}"
    echo -e "${CYAN}Location:${NC}      ${DIST_DIR}/${DEB_FILENAME}"
    echo ""
    echo -e "${CYAN}Installation:${NC}"
    echo "  sudo dpkg -i ${DIST_DIR}/${DEB_FILENAME}"
    echo "  sudo apt-get install -f  # Fix dependencies if needed"
    echo ""
    echo -e "${CYAN}Removal:${NC}"
    echo "  sudo apt-get remove ${APP_NAME}"
    echo ""
}

# Main build process
main() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}  ${GREEN}Open Media Converter - Debian Package Builder${NC}          ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    check_prerequisites
    build_jar
    prepare_build_directory
    copy_control_files
    copy_jar
    copy_launcher
    copy_desktop_entry
    copy_icons
    copy_embedded_binaries
    set_permissions
    calculate_installed_size
    build_deb_package
    validate_package
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}  ${GREEN}✓ Debian Package Built Successfully${NC}                    ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    show_package_info
}

# Run main function
main "$@"
