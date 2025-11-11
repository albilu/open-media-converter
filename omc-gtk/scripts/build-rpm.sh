#!/usr/bin/env bash
###############################################################################
# Open Media Converter - RPM Package Build Script
#
# This script builds a .rpm package for Open Media Converter for Red Hat,
# Fedora, and other RPM-based distributions.
#
# Prerequisites:
#   - Maven 3.8+ (for building JAR)
#   - rpmbuild (from rpm-build package)
#   - rpmdevtools (optional, for rpm development tools)
#
# Usage: ./scripts/build-rpm.sh [OPTIONS]
#
# Options:
#   --skip-build    Skip Maven build (use existing JAR)
#   --no-validate   Skip rpmlint validation
#   --help          Show this help message
#
# Output: dist/open-media-converter-1.0.0-1.x86_64.rpm
###############################################################################

set -e  # Exit on error

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OMC_GTK_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$OMC_GTK_ROOT")"
BUILD_DIR="${PROJECT_ROOT}/build/rpm"
DIST_DIR="${PROJECT_ROOT}/dist"
PACKAGING_DIR="${OMC_GTK_ROOT}/packaging/rpm"

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
RELEASE="1"
ARCH="x86_64"
RPM_FILENAME="${APP_NAME}-${APP_VERSION}-${RELEASE}.${ARCH}.rpm"

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
    
    if ! command -v rpmbuild &> /dev/null; then
        log_error "rpmbuild not found. Install with:"
        log_error "  Fedora/RHEL: sudo dnf install rpm-build"
        log_error "  OpenSUSE: sudo zypper install rpm-build"
        missing=true
    else
        log_success "rpmbuild found"
    fi
    
    if [ "$SKIP_BUILD" = false ] && ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Install with:"
        log_error "  Fedora/RHEL: sudo dnf install maven"
        log_error "  OpenSUSE: sudo zypper install maven"
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
        if command -v rpmlint &> /dev/null; then
            log_success "rpmlint found (will validate package)"
        else
            log_warn "rpmlint not found (package validation will be skipped)"
            log_warn "Install with: sudo dnf install rpmlint"
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

# Set up RPM build environment
setup_build_env() {
    log_step "Setting up RPM build environment"
    
    # Create rpmbuild directory structure
    mkdir -p "${BUILD_DIR}"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
    
    # Create staging directory for file layout
    local staging="${BUILD_DIR}/staging"
    rm -rf "$staging"
    mkdir -p "$staging"
    
    # Create directory structure
    mkdir -p "${staging}/opt/${APP_NAME}/bin"
    mkdir -p "${staging}/opt/${APP_NAME}/lib"
    mkdir -p "${staging}/usr/bin"
    mkdir -p "${staging}/usr/share/applications"
    mkdir -p "${staging}/usr/share/icons/hicolor/"{16x16,32x32,48x48,64x64,128x128,256x256,scalable}"/apps"
    mkdir -p "${staging}/usr/share/doc/${APP_NAME}"
    
    log_success "Build directories created"
}

# Copy application files
copy_app_files() {
    log_step "Copying application files"
    
    local staging="${BUILD_DIR}/staging"
    local jar_path="${OMC_GTK_ROOT}/target/${APP_NAME}-${APP_SNAPSHOT_VERSION}.jar"
    
    # Copy JAR
    if [ -f "$jar_path" ]; then
        cp "$jar_path" "${staging}/opt/${APP_NAME}/lib/${APP_NAME}-${APP_VERSION}.jar"
        log_success "Copied JAR"
    else
        log_error "JAR not found: $jar_path"
        exit 1
    fi
    
    # Copy launcher script
    if [ -f "${OMC_GTK_ROOT}/bin/open-media-converter" ]; then
        cp "${OMC_GTK_ROOT}/bin/open-media-converter" "${staging}/opt/${APP_NAME}/bin/${APP_NAME}"
        chmod 755 "${staging}/opt/${APP_NAME}/bin/${APP_NAME}"
        log_success "Copied launcher script"
    else
        log_error "Launcher script not found"
        exit 1
    fi
    
    # Copy desktop entry
    mkdir -p "${staging}/usr/share/applications"
    cat > "${staging}/usr/share/applications/${APP_NAME}.desktop" <<'EOF'
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
        for size in 16x16 32x32 48x48 64x64 128x128 256x256; do
            if [ -f "${icons_src}/${size}/apps/open-media-converter.svg" ]; then
                cp "${icons_src}/${size}/apps/open-media-converter.svg" \
                   "${staging}/usr/share/icons/hicolor/${size}/apps/"
            fi
        done
        if [ -f "${icons_src}/scalable/apps/open-media-converter.svg" ]; then
            cp "${icons_src}/scalable/apps/open-media-converter.svg" \
               "${staging}/usr/share/icons/hicolor/scalable/apps/"
        fi
        log_success "Copied icons"
    else
        log_warn "Icons directory not found, skipping"
    fi
    
    # Copy documentation
    for doc in README.md CHANGELOG.md LICENSE; do
        if [ -f "${PROJECT_ROOT}/$doc" ]; then
            cp "${PROJECT_ROOT}/$doc" "${staging}/usr/share/doc/${APP_NAME}/"
        fi
    done
    log_success "Copied documentation"
}

# Create tarball for rpmbuild
create_source_tarball() {
    log_step "Creating source tarball"
    
    local staging="${BUILD_DIR}/staging"
    local tarball_name="${APP_NAME}-${APP_VERSION}"
    local sources_dir="${BUILD_DIR}/SOURCES"
    
    # Create directory structure expected by spec file
    mkdir -p "${BUILD_DIR}/temp/${tarball_name}"/{bin,lib,share}
    
    # Copy files
    cp -r "${staging}/opt/${APP_NAME}/bin" "${BUILD_DIR}/temp/${tarball_name}/"
    cp -r "${staging}/opt/${APP_NAME}/lib" "${BUILD_DIR}/temp/${tarball_name}/"
    cp -r "${staging}/usr/share"/* "${BUILD_DIR}/temp/${tarball_name}/share/"
    
    # Create tarball
    cd "${BUILD_DIR}/temp"
    tar czf "${sources_dir}/${tarball_name}.tar.gz" "${tarball_name}"
    cd "$PROJECT_ROOT"
    
    # Clean up temp directory
    rm -rf "${BUILD_DIR}/temp"
    
    log_success "Source tarball created"
}

# Copy spec file
copy_spec_file() {
    log_step "Copying RPM spec file"
    
    if [ -f "${PACKAGING_DIR}/SPECS/open-media-converter.spec" ]; then
        cp "${PACKAGING_DIR}/SPECS/open-media-converter.spec" "${BUILD_DIR}/SPECS/"
        
        # Update version in spec file dynamically
        sed -i "s/^Version:.*/Version:        ${APP_VERSION}/" "${BUILD_DIR}/SPECS/open-media-converter.spec"
        
        # Update JAR filename in spec file (install and files sections)
        # Match both literal app name and RPM %{name} variable
        sed -i "s/${APP_NAME}-[0-9.]*\.jar/${APP_NAME}-${APP_VERSION}.jar/g" "${BUILD_DIR}/SPECS/open-media-converter.spec"
        sed -i "s/%{name}-[0-9.]*\.jar/%{name}-${APP_VERSION}.jar/g" "${BUILD_DIR}/SPECS/open-media-converter.spec"
        
        log_success "Spec file copied and updated (version: ${APP_VERSION})"
    else
        log_error "Spec file not found: ${PACKAGING_DIR}/SPECS/open-media-converter.spec"
        exit 1
    fi
}

# Build RPM package
build_rpm_package() {
    log_step "Building RPM package"
    
    cd "${BUILD_DIR}"
    
    # Build the RPM
    if rpmbuild \
        --define "_topdir ${BUILD_DIR}" \
        --define "_rpmdir ${BUILD_DIR}/RPMS" \
        --define "_srcrpmdir ${BUILD_DIR}/SRPMS" \
        -bb SPECS/open-media-converter.spec; then
        log_success "RPM package built"
    else
        log_error "rpmbuild failed"
        exit 1
    fi
    
    cd "$PROJECT_ROOT"
}

# Copy package to dist directory
copy_to_dist() {
    log_step "Copying package to dist directory"
    
    mkdir -p "$DIST_DIR"
    
    local rpm_file="${BUILD_DIR}/RPMS/${ARCH}/${RPM_FILENAME}"
    
    if [ -f "$rpm_file" ]; then
        cp "$rpm_file" "$DIST_DIR/"
        log_success "Package copied to: ${DIST_DIR}/${RPM_FILENAME}"
    else
        log_error "RPM file not found: $rpm_file"
        exit 1
    fi
}

# Validate package with rpmlint
validate_package() {
    if [ "$NO_VALIDATE" = true ]; then
        log_step "Skipping package validation"
        return
    fi
    
    if ! command -v rpmlint &> /dev/null; then
        log_warn "rpmlint not found, skipping validation"
        return
    fi
    
    log_step "Validating package with rpmlint"
    
    local rpm_file="${DIST_DIR}/${RPM_FILENAME}"
    
    if rpmlint "$rpm_file" 2>&1 | tee /tmp/rpmlint.log; then
        log_success "Package validation completed"
    else
        log_warn "rpmlint found some issues (see above)"
        log_warn "This does not necessarily mean the package is broken"
    fi
}

# Show package information
show_package_info() {
    log_step "Package Information"
    
    local rpm_file="${DIST_DIR}/${RPM_FILENAME}"
    
    if [ -f "$rpm_file" ]; then
        echo ""
        echo -e "${CYAN}File:${NC}     $(basename "$rpm_file")"
        echo -e "${CYAN}Size:${NC}     $(du -h "$rpm_file" | cut -f1)"
        echo -e "${CYAN}Path:${NC}     ${rpm_file}"
        echo ""
        
        echo -e "${GREEN}Package Details:${NC}"
        rpm -qip "$rpm_file" 2>/dev/null | grep -E "^(Name|Version|Release|Architecture|Size|Summary)" || true
        echo ""
        
        echo -e "${GREEN}Installation:${NC}"
        echo "  sudo dnf install ${rpm_file}"
        echo "  OR"
        echo "  sudo rpm -ivh ${rpm_file}"
        echo ""
        
        echo -e "${GREEN}Upgrade existing installation:${NC}"
        echo "  sudo dnf upgrade ${rpm_file}"
        echo "  OR"
        echo "  sudo rpm -Uvh ${rpm_file}"
        echo ""
    fi
}

# Main build process
main() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}  Open Media Converter - RPM Package Builder              ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    check_prerequisites
    build_jar
    setup_build_env
    copy_app_files
    create_source_tarball
    copy_spec_file
    build_rpm_package
    copy_to_dist
    validate_package
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}  ${GREEN}✓ RPM Package Built Successfully${NC}                        ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    show_package_info
}

# Run main function
main "$@"
