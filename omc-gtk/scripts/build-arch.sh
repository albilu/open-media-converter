#!/usr/bin/env bash
###############################################################################
# Open Media Converter - Arch Linux Package Build Script
#
# This script builds a .pkg.tar.zst package for Open Media Converter for
# Arch Linux and Arch-based distributions.
#
# Prerequisites:
#   - Maven 3.8+ (for building JAR)
#   - makepkg (from pacman package)
#   - namcap (optional, for package validation)
#
# Usage: ./scripts/build-arch.sh [OPTIONS]
#
# Options:
#   --skip-build    Skip Maven build (use existing JAR)
#   --no-validate   Skip namcap validation
#   --help          Show this help message
#
# Output: dist/open-media-converter-1.0.0-1-x86_64.pkg.tar.zst
###############################################################################

set -e  # Exit on error

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OMC_GTK_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$OMC_GTK_ROOT")"
BUILD_DIR="${PROJECT_ROOT}/build/arch"
DIST_DIR="${PROJECT_ROOT}/dist"
PACKAGING_DIR="${OMC_GTK_ROOT}/packaging/arch"

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
PKGREL="1"
ARCH="x86_64"
PKG_FILENAME="${APP_NAME}-${APP_VERSION}-${PKGREL}-${ARCH}.pkg.tar.zst"

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
    
    if ! command -v makepkg &> /dev/null; then
        log_error "makepkg not found. Install with:"
        log_error "  sudo pacman -S base-devel"
        missing=true
    else
        log_success "makepkg found"
    fi
    
    if [ "$SKIP_BUILD" = false ] && ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Install with:"
        log_error "  sudo pacman -S maven"
        missing=true
    else
        if [ "$SKIP_BUILD" = false ]; then
            log_success "Maven found: $(mvn -version | head -1 | awk '{print $3}')"
        fi
    fi
    
    if [ "$missing" = true ]; then
        exit 1
    fi
    
    if [ "$NO_VALIDATE" = false ]; then
        if command -v namcap &> /dev/null; then
            log_success "namcap found (will validate package)"
        else
            log_warn "namcap not found (package validation will be skipped)"
            log_warn "Install with: sudo pacman -S namcap"
        fi
    fi
}

# Build JAR with Maven
build_jar() {
    if [ "$SKIP_BUILD" = true ]; then
        log_step "Skipping Maven build"
        
        local jar_path="${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
        if [ ! -f "$jar_path" ]; then
            log_error "JAR not found: $jar_path"
            log_error "Run without --skip-build to build JAR first"
            exit 1
        fi
        
        log_info "Using existing JAR: $(basename "$jar_path")"
        return
    fi
    
    log_step "Building application JAR"
    
    cd "$PROJECT_ROOT"
    
    if mvn clean package -DskipTests; then
        log_success "JAR built successfully"
    else
        log_error "Maven build failed"
        exit 1
    fi
}

# Set up build environment
setup_build_env() {
    log_step "Setting up build environment" >&2
    
    # Clean and create build directory
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    
    # Create source package directory structure
    local src_dir="${BUILD_DIR}/${APP_NAME}-${APP_VERSION}"
    mkdir -p "${src_dir}"/{bin,lib,share/{applications,icons/hicolor,doc/${APP_NAME}}}
    
    log_success "Build directories created" >&2
    
    echo "$src_dir"
}

# Copy application files
copy_app_files() {
    local src_dir="$1"
    
    log_step "Copying application files"
    
    local jar_path="${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
    
    # Copy JAR
    if [ -f "$jar_path" ]; then
        cp "$jar_path" "${src_dir}/lib/${APP_NAME}-${APP_VERSION}.jar"
        log_success "Copied JAR"
    else
        log_error "JAR not found: $jar_path"
        exit 1
    fi
    
    # Copy launcher script
    if [ -f "${OMC_GTK_ROOT}/bin/open-media-converter" ]; then
        cp "${OMC_GTK_ROOT}/bin/open-media-converter" "${src_dir}/bin/${APP_NAME}"
        chmod 755 "${src_dir}/bin/${APP_NAME}"
        log_success "Copied launcher script"
    else
        log_error "Launcher script not found"
        exit 1
    fi
    
    # Create desktop entry
    cat > "${src_dir}/share/applications/${APP_NAME}.desktop" <<'EOF'
[Desktop Entry]
Version=1.0
Type=Application
Name=Open Media Converter
GenericName=Media Converter
Comment=Convert video, audio, image, and document formats
Exec=open-media-converter %F
Icon=open-media-converter
Terminal=false
Categories=AudioVideo;Audio;Video;Graphics;Office;Utility;
MimeType=video/mp4;video/x-matroska;video/avi;audio/mpeg;audio/flac;image/jpeg;image/png;application/pdf;
Keywords=convert;transcode;media;video;audio;image;document;
StartupNotify=true
EOF
    log_success "Created desktop entry"
    
    # Copy icons
    local icons_src="${OMC_GTK_ROOT}/src/main/resources/icons/hicolor"
    if [ -d "$icons_src" ]; then
        for size in 16x16 32x32 48x48 64x64 128x128 256x256 scalable; do
            mkdir -p "${src_dir}/share/icons/hicolor/${size}/apps"
            if [ "$size" = "scalable" ]; then
                if [ -f "${icons_src}/${size}/apps/open-media-converter.svg" ]; then
                    cp "${icons_src}/${size}/apps/open-media-converter.svg" \
                       "${src_dir}/share/icons/hicolor/${size}/apps/"
                fi
            else
                if [ -f "${icons_src}/${size}/apps/open-media-converter.png" ]; then
                    cp "${icons_src}/${size}/apps/open-media-converter.png" \
                       "${src_dir}/share/icons/hicolor/${size}/apps/"
                fi
            fi
        done
        log_success "Copied icons"
    else
        log_warn "Icons directory not found, skipping"
    fi
    
    # Copy documentation
    for doc in README.md CHANGELOG.md LICENSE; do
        if [ -f "${PROJECT_ROOT}/$doc" ]; then
            cp "${PROJECT_ROOT}/$doc" "${src_dir}/share/doc/${APP_NAME}/"
        fi
    done
    log_success "Copied documentation"
}

# Create source tarball
create_source_tarball() {
    log_step "Creating source tarball"
    
    cd "$BUILD_DIR"
    
    # Create tarball
    tar czf "${APP_NAME}-${APP_VERSION}.tar.gz" "${APP_NAME}-${APP_VERSION}"
    
    log_success "Source tarball created"
}

# Copy PKGBUILD and install script
copy_pkgbuild() {
    log_step "Copying PKGBUILD"
    
    if [ -f "${PACKAGING_DIR}/PKGBUILD" ]; then
        cp "${PACKAGING_DIR}/PKGBUILD" "${BUILD_DIR}/"
        
        # Update version in PKGBUILD dynamically
        sed -i "s/^pkgver=.*/pkgver=${APP_VERSION}/" "${BUILD_DIR}/PKGBUILD"
        
        log_success "PKGBUILD copied and updated (version: ${APP_VERSION})"
    else
        log_error "PKGBUILD not found: ${PACKAGING_DIR}/PKGBUILD"
        exit 1
    fi
    
    # Copy install script if it exists
    if [ -f "${PACKAGING_DIR}/.INSTALL" ]; then
        cp "${PACKAGING_DIR}/.INSTALL" "${BUILD_DIR}/${APP_NAME}.install"
        log_success "Install script copied"
    fi
}

# Build package with makepkg
build_package() {
    log_step "Building Arch package"
    
    cd "$BUILD_DIR"
    
    # Build the package
    # --nodeps: Don't check dependencies (they might not be available on non-Arch systems)
    # --skipinteg: Skip integrity checks (we're building from local source)
    if PKGEXT='.pkg.tar.zst' makepkg --skipinteg --nodeps -f; then
        log_success "Package built successfully"
    else
        log_error "makepkg failed"
        exit 1
    fi
    
    cd "$PROJECT_ROOT"
}

# Copy package to dist directory
copy_to_dist() {
    log_step "Copying package to dist directory"
    
    mkdir -p "$DIST_DIR"
    
    local pkg_file="${BUILD_DIR}/${PKG_FILENAME}"
    
    if [ -f "$pkg_file" ]; then
        cp "$pkg_file" "$DIST_DIR/"
        log_success "Package copied to: ${DIST_DIR}/${PKG_FILENAME}"
    else
        log_error "Package file not found: $pkg_file"
        # Try to find any .pkg.tar.zst file
        local found_pkg=$(find "$BUILD_DIR" -name "*.pkg.tar.zst" -type f | head -1)
        if [ -n "$found_pkg" ]; then
            cp "$found_pkg" "$DIST_DIR/"
            log_success "Package copied: $(basename "$found_pkg")"
        else
            exit 1
        fi
    fi
}

# Validate package with namcap
validate_package() {
    if [ "$NO_VALIDATE" = true ]; then
        log_step "Skipping package validation"
        return
    fi
    
    if ! command -v namcap &> /dev/null; then
        log_warn "namcap not found, skipping validation"
        return
    fi
    
    log_step "Validating package with namcap"
    
    # Find the package file
    local pkg_file=$(find "$DIST_DIR" -name "${APP_NAME}*.pkg.tar.zst" -type f | head -1)
    
    if [ -z "$pkg_file" ]; then
        log_warn "Package file not found for validation"
        return
    fi
    
    if namcap "$pkg_file" 2>&1 | tee /tmp/namcap.log; then
        log_success "Package validation completed"
    else
        log_warn "namcap found some issues (see above)"
        log_warn "This does not necessarily mean the package is broken"
    fi
    
    # Also validate PKGBUILD
    if [ -f "${BUILD_DIR}/PKGBUILD" ]; then
        log_step "Validating PKGBUILD"
        namcap "${BUILD_DIR}/PKGBUILD" 2>&1 || true
    fi
}

# Show package information
show_package_info() {
    log_step "Package Information"
    
    local pkg_file=$(find "$DIST_DIR" -name "${APP_NAME}*.pkg.tar.zst" -type f | head -1)
    
    if [ -n "$pkg_file" ] && [ -f "$pkg_file" ]; then
        echo ""
        echo -e "${CYAN}File:${NC}     $(basename "$pkg_file")"
        echo -e "${CYAN}Size:${NC}     $(du -h "$pkg_file" | cut -f1)"
        echo -e "${CYAN}Path:${NC}     ${pkg_file}"
        echo ""
        
        echo -e "${GREEN}Installation:${NC}"
        echo "  sudo pacman -U ${pkg_file}"
        echo ""
        
        echo -e "${GREEN}Package Contents:${NC}"
        tar -tzf "$pkg_file" 2>/dev/null | head -20 || true
        echo "  ..."
        echo ""
    fi
}

# Main build process
main() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}  Open Media Converter - Arch Linux Package Builder       ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    check_prerequisites
    build_jar
    local src_dir=$(setup_build_env)
    copy_app_files "$src_dir"
    create_source_tarball
    copy_pkgbuild
    build_package
    copy_to_dist
    validate_package
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}  ${GREEN}✓ Arch Package Built Successfully${NC}                        ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    show_package_info
}

# Run main function
main "$@"
