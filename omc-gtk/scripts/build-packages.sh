#!/usr/bin/env bash
###############################################################################
# Open Media Converter - Universal Package Build Script
#
# This script orchestrates the build of all package formats,
# providing a unified interface for creating distribution packages.
#
# Prerequisites:
#   - Maven 3.8+ (for building JAR)
#   - dpkg-deb (for DEB package) - install with `sudo apt install dpkg-dev`
#   - rpmbuild (for RPM package) - install with `sudo dnf install rpm-build`
#   - makepkg (for Arch package) - install with `sudo pacman -S base-devel`
#   - wget or curl (for AppImage dependencies)
#   - tar, gzip (for extracting archives)
#   - appimagetool (for AppImage) - auto-downloaded if needed
#
# Usage: ./scripts/build-packages.sh [OPTIONS]
#
# Options:
#   --deb                Build only Debian package
#   --rpm                Build only RPM package
#   --arch               Build only Arch Linux package
#   --appimage           Build only AppImage
#   --all                Build all packages (default)
#   --skip-build         Skip Maven build (use existing JAR)
#   --no-embedded-tools  Skip embedding ffmpeg/pandoc in AppImage
#   --clean              Clean build/ directory before building (dist/ always cleaned)
#   --help               Show this help message
#
# Output:
#   - dist/open-media-converter_1.0.0_amd64.deb
#   - dist/open-media-converter-1.0.0-1.x86_64.rpm
#   - dist/open-media-converter-1.0.0-1-x86_64.pkg.tar.zst
#   - dist/Open_Media_Converter-1.0.0-x86_64.AppImage
#
# Examples:
#   ./scripts/build-packages.sh                    # Build everything
#   ./scripts/build-packages.sh --deb              # DEB only
#   ./scripts/build-packages.sh --rpm              # RPM only
#   ./scripts/build-packages.sh --arch             # Arch only
#   ./scripts/build-packages.sh --appimage         # AppImage only
#   ./scripts/build-packages.sh --clean --all      # Clean build
#   ./scripts/build-packages.sh --skip-build       # Fast rebuild
#
###############################################################################

set -e  # Exit on error

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OMC_GTK_ROOT="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$OMC_GTK_ROOT")"
DIST_DIR="${PROJECT_ROOT}/dist"
BUILD_DIR="${PROJECT_ROOT}/build"

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
    echo "1.0.0"
}

# Application metadata
APP_NAME="Open Media Converter"
APP_SNAPSHOT_VERSION="$(detect_version)"  # Keep SNAPSHOT for JAR filename
APP_VERSION="${APP_SNAPSHOT_VERSION%-SNAPSHOT}"  # Remove -SNAPSHOT suffix for package version

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m'

# Build flags
BUILD_DEB=false
BUILD_RPM=false
BUILD_ARCH=false
BUILD_APPIMAGE=false
SKIP_BUILD=false
NO_EMBEDDED_TOOLS=false
CLEAN_BUILD=false

# Parse command-line options
if [ $# -eq 0 ]; then
    # Default: build all
    BUILD_DEB=true
    BUILD_RPM=true
    BUILD_ARCH=true
    BUILD_APPIMAGE=true
fi

while [[ $# -gt 0 ]]; do
    case $1 in
        --deb)
            BUILD_DEB=true
            shift
            ;;
        --rpm)
            BUILD_RPM=true
            shift
            ;;
        --arch)
            BUILD_ARCH=true
            shift
            ;;
        --appimage)
            BUILD_APPIMAGE=true
            shift
            ;;
        --all)
            BUILD_DEB=true
            BUILD_RPM=true
            BUILD_ARCH=true
            BUILD_APPIMAGE=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --no-embedded-tools)
            NO_EMBEDDED_TOOLS=true
            shift
            ;;
        --clean)
            CLEAN_BUILD=true
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
log_banner() {
    echo ""
    echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${MAGENTA}║${NC}  ${BOLD}$1${NC}"
    echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

log_section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

log_step() {
    echo -e "${CYAN}▶${NC} $1"
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

log_info() {
    echo -e "${NC}  $1"
}

# Track timing
START_TIME=$(date +%s)
declare -A PHASE_TIMES

start_phase() {
    PHASE_START=$(date +%s)
}

end_phase() {
    local phase_name="$1"
    local phase_end=$(date +%s)
    local phase_duration=$((phase_end - PHASE_START))
    PHASE_TIMES["$phase_name"]=$phase_duration
}

format_duration() {
    local seconds=$1
    local minutes=$((seconds / 60))
    local remaining_seconds=$((seconds % 60))
    
    if [ $minutes -gt 0 ]; then
        echo "${minutes}m ${remaining_seconds}s"
    else
        echo "${seconds}s"
    fi
}

# Check prerequisites
check_prerequisites() {
    log_section "Checking Prerequisites"
    
    local missing=false
    
    # Check Maven
    if [ "$SKIP_BUILD" = false ] && ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Install with: sudo apt install maven"
        missing=true
    else
        if [ "$SKIP_BUILD" = false ]; then
            local mvn_version=$(mvn -version | head -1 | awk '{print $3}')
            log_success "Maven found: $mvn_version"
        fi
    fi
    
    # Check DEB prerequisites
    if [ "$BUILD_DEB" = true ]; then
        if ! command -v dpkg-deb &> /dev/null; then
            log_error "dpkg-deb not found. Install with: sudo apt install dpkg-dev"
            missing=true
        else
            log_success "dpkg-deb found"
        fi
    fi
    
    # Check AppImage prerequisites
    if [ "$BUILD_APPIMAGE" = true ]; then
        if ! command -v wget &> /dev/null && ! command -v curl &> /dev/null; then
            log_error "Neither wget nor curl found. Install with: sudo apt install wget"
            missing=true
        else
            log_success "Download tool found (wget or curl)"
        fi
        
        if ! command -v tar &> /dev/null; then
            log_error "tar not found. Install with: sudo apt install tar"
            missing=true
        else
            log_success "tar found"
        fi
    fi
    
    if [ "$missing" = true ]; then
        echo ""
        log_error "Missing prerequisites. Please install required tools."
        exit 1
    fi
    
    echo ""
    log_success "All prerequisites satisfied"
}

# Ensure dist/ is clean before packaging (always runs)
ensure_dist_clean() {
    log_section "Preparing Distribution Directory"
    
    if [ -d "$DIST_DIR" ]; then
        log_step "Cleaning dist/ directory to ensure fresh packages..."
        rm -rf "$DIST_DIR"
        log_success "dist/ cleaned"
    fi
    
    log_step "Creating dist/ directory..."
    mkdir -p "$DIST_DIR"
    log_success "dist/ ready"
    
    echo ""
}

# Clean previous builds (runs only with --clean flag)
clean_builds() {
    if [ "$CLEAN_BUILD" = false ]; then
        return
    fi
    
    log_section "Cleaning Build Artifacts"
    
    if [ -d "$BUILD_DIR" ]; then
        log_step "Removing build/ directory..."
        rm -rf "$BUILD_DIR"
        log_success "build/ cleaned"
    fi
    
    echo ""
    log_success "Clean complete"
}

# Build JAR with Maven
build_jar() {
    if [ "$SKIP_BUILD" = true ]; then
        log_section "Skipping Maven Build"
        
        # Check if JAR exists (try with SNAPSHOT version from POM)
        local jar_path="${OMC_GTK_ROOT}/target/open-media-converter-${APP_SNAPSHOT_VERSION}.jar"
        if [ ! -f "$jar_path" ]; then
            log_error "JAR not found: $jar_path"
            log_error "Run without --skip-build to build JAR first"
            exit 1
        fi
        
        local jar_size=$(du -h "$jar_path" | cut -f1)
        log_info "Using existing JAR: $(basename "$jar_path") (${jar_size})"
        return
    fi
    
    log_section "Building Application JAR"
    
    start_phase
    
    log_step "Running Maven clean package..."
    cd "$PROJECT_ROOT"
    
    # Capture Maven output and show progress
    if mvn clean package -DskipTests 2>&1 | while IFS= read -r line; do
        if [[ "$line" =~ "Building jar:" ]] || [[ "$line" =~ "BUILD SUCCESS" ]]; then
            echo "  $line"
        fi
    done; then
        local jar_path="${OMC_GTK_ROOT}/target/open-media-converter-${APP_SNAPSHOT_VERSION}.jar"
        if [ -f "$jar_path" ]; then
            local jar_size=$(du -h "$jar_path" | cut -f1)
            log_success "JAR built successfully: $(basename "$jar_path") (${jar_size})"
        else
            log_error "JAR not found at expected location: $jar_path"
            exit 1
        fi
    else
        log_error "Maven build failed"
        exit 1
    fi
    
    end_phase "Maven Build"
}

# Build Debian package
build_deb() {
    if [ "$BUILD_DEB" = false ]; then
        return
    fi
    
    log_section "Building Debian Package"
    
    start_phase
    
    local deb_script="${SCRIPT_DIR}/build-deb.sh"
    
    if [ ! -x "$deb_script" ]; then
        log_error "DEB build script not found or not executable: $deb_script"
        exit 1
    fi
    
    # Build arguments
    local deb_args=""
    if [ "$SKIP_BUILD" = true ]; then
        deb_args="$deb_args --skip-build"
    fi
    deb_args="$deb_args --no-validate"  # Skip lintian in batch mode
    
    log_step "Running build-deb.sh..."
    
    # Run DEB build script and capture output
    local deb_output=$(mktemp)
    set +e  # Temporarily disable exit on error to capture exit code
    bash "$deb_script" $deb_args > "$deb_output" 2>&1
    local deb_exit_code=$?
    set -e
    
    # Show filtered output (include all log markers: ▶ ✓ ✗ ━ ⚠)
    grep -E "(▶|✓|✗|━|⚠|Package built|JAR)" "$deb_output" | while IFS= read -r line; do
        echo "  $line"
    done
    rm -f "$deb_output"
    
    # Check if DEB build succeeded
    if [ $deb_exit_code -eq 0 ] && [ -f "${DIST_DIR}/open-media-converter_${APP_VERSION}_amd64.deb" ]; then
        log_success "Debian package built"
    else
        log_error "Debian package build failed"
        exit 1
    fi
    
    end_phase "DEB Build"
}

# Build RPM package
build_rpm() {
    if [ "$BUILD_RPM" = false ]; then
        return
    fi
    
    log_section "Building RPM Package"
    
    start_phase
    
    local rpm_script="${SCRIPT_DIR}/build-rpm.sh"
    
    if [ ! -x "$rpm_script" ]; then
        log_error "RPM build script not found or not executable: $rpm_script"
        exit 1
    fi
    
    # Build arguments
    local rpm_args=""
    if [ "$SKIP_BUILD" = true ]; then
        rpm_args="$rpm_args --skip-build"
    fi
    rpm_args="$rpm_args --no-validate"  # Skip rpmlint in batch mode
    
    log_step "Running build-rpm.sh..."
    
    # Run RPM build script and capture output
    local rpm_output=$(mktemp)
    set +e  # Temporarily disable exit on error to capture exit code
    bash "$rpm_script" $rpm_args > "$rpm_output" 2>&1
    local rpm_exit_code=$?
    set -e
    
    # Show filtered output (include all log markers: ▶ ✓ ✗ ━ ⚠)
    grep -E "(▶|✓|✗|━|⚠|Package built|JAR)" "$rpm_output" | while IFS= read -r line; do
        echo "  $line"
    done
    rm -f "$rpm_output"
    
    # Check if RPM build succeeded
    if [ $rpm_exit_code -eq 0 ] && [ -f "${DIST_DIR}/open-media-converter-${APP_VERSION}-1.x86_64.rpm" ]; then
        log_success "RPM package built"
    else
        log_error "RPM package build failed"
        exit 1
    fi
    
    end_phase "RPM Build"
}

# Build Arch Linux package
build_arch() {
    if [ "$BUILD_ARCH" = false ]; then
        return
    fi
    
    log_section "Building Arch Linux Package"
    
    start_phase
    
    # Check if makepkg is available
    if ! command -v makepkg &> /dev/null; then
        log_warn "makepkg not found - skipping Arch package"
        log_warn "Arch packages can only be built on Arch Linux systems"
        return
    fi
    
    local arch_script="${SCRIPT_DIR}/build-arch.sh"
    
    if [ ! -x "$arch_script" ]; then
        log_error "Arch build script not found or not executable: $arch_script"
        exit 1
    fi
    
    # Build arguments
    local arch_args=""
    if [ "$SKIP_BUILD" = true ]; then
        arch_args="$arch_args --skip-build"
    fi
    arch_args="$arch_args --no-validate"  # Skip namcap in batch mode
    
    log_step "Running build-arch.sh..."
    
    # Run Arch build script and capture output
    local arch_output=$(mktemp)
    set +e  # Temporarily disable exit on error to capture exit code
    bash "$arch_script" $arch_args > "$arch_output" 2>&1
    local arch_exit_code=$?
    set -e
    
    # Show filtered output (include all log markers: ▶ ✓ ✗ ━ ⚠)
    grep -E "(▶|✓|✗|━|⚠|Package built|JAR)" "$arch_output" | while IFS= read -r line; do
        echo "  $line"
    done
    rm -f "$arch_output"
    
    # Check if Arch build succeeded
    local arch_file=$(find "${DIST_DIR}" -name "open-media-converter-*.pkg.tar.zst" -type f 2>/dev/null | head -1)
    if [ $arch_exit_code -eq 0 ] && [ -n "$arch_file" ] && [ -f "$arch_file" ]; then
        log_success "Arch package built"
    else
        log_error "Arch package build failed"
        exit 1
    fi
    
    end_phase "Arch Build"
}

# Build AppImage
build_appimage() {
    if [ "$BUILD_APPIMAGE" = false ]; then
        return
    fi
    
    log_section "Building AppImage"
    
    start_phase
    
    local appimage_script="${SCRIPT_DIR}/build-appimage.sh"
    
    if [ ! -x "$appimage_script" ]; then
        log_error "AppImage build script not found or not executable: $appimage_script"
        exit 1
    fi
    
    # Build arguments
    local appimage_args=""
    if [ "$SKIP_BUILD" = true ]; then
        appimage_args="$appimage_args --skip-build"
    fi
    if [ "$NO_EMBEDDED_TOOLS" = true ]; then
        appimage_args="$appimage_args --no-embedded-tools"
    fi
    
    log_step "Running build-appimage.sh..."
    
    # Run AppImage build script and capture output
    local appimage_output=$(mktemp)
    set +e  # Temporarily disable exit on error to capture exit code
    bash "$appimage_script" $appimage_args > "$appimage_output" 2>&1
    local appimage_exit_code=$?
    set -e
    
    # Show filtered output (include all log markers: ▶ ✓ ✗ ━ ⚠ ↓)
    grep -E "(▶|✓|✗|━|⚠|↓|AppImage built|JAR)" "$appimage_output" | while IFS= read -r line; do
        echo "  $line"
    done
    rm -f "$appimage_output"
    
    # Check if AppImage build succeeded
    if [ $appimage_exit_code -eq 0 ] && [ -f "${DIST_DIR}/Open_Media_Converter-${APP_VERSION}-x86_64.AppImage" ]; then
        log_success "AppImage built"
    else
        log_error "AppImage build failed"
        exit 1
    fi
    
    end_phase "AppImage Build"
}

# Show build summary
show_summary() {
    local end_time=$(date +%s)
    local total_duration=$((end_time - START_TIME))
    
    log_section "Build Summary"
    
    echo ""
    echo -e "${BOLD}${APP_NAME} v${APP_VERSION}${NC}"
    echo ""
    
    # Show built packages
    if [ "$BUILD_DEB" = true ]; then
        local deb_file="${DIST_DIR}/open-media-converter_${APP_VERSION}_amd64.deb"
        if [ -f "$deb_file" ]; then
            local deb_size=$(du -h "$deb_file" | cut -f1)
            echo -e "${GREEN}✓${NC} Debian Package:"
            echo -e "  ${CYAN}File:${NC}     $(basename "$deb_file")"
            echo -e "  ${CYAN}Size:${NC}     ${deb_size}"
            echo -e "  ${CYAN}Path:${NC}     ${deb_file}"
            echo ""
        fi
    fi
    
    if [ "$BUILD_RPM" = true ]; then
        local rpm_file="${DIST_DIR}/open-media-converter-${APP_VERSION}-1.x86_64.rpm"
        if [ -f "$rpm_file" ]; then
            local rpm_size=$(du -h "$rpm_file" | cut -f1)
            echo -e "${GREEN}✓${NC} RPM Package:"
            echo -e "  ${CYAN}File:${NC}     $(basename "$rpm_file")"
            echo -e "  ${CYAN}Size:${NC}     ${rpm_size}"
            echo -e "  ${CYAN}Path:${NC}     ${rpm_file}"
            echo ""
        fi
    fi
    
    if [ "$BUILD_ARCH" = true ]; then
        local arch_file=$(find "${DIST_DIR}" -name "open-media-converter-*.pkg.tar.zst" -type f 2>/dev/null | head -1)
        if [ -n "$arch_file" ] && [ -f "$arch_file" ]; then
            local arch_size=$(du -h "$arch_file" | cut -f1)
            echo -e "${GREEN}✓${NC} Arch Linux Package:"
            echo -e "  ${CYAN}File:${NC}     $(basename "$arch_file")"
            echo -e "  ${CYAN}Size:${NC}     ${arch_size}"
            echo -e "  ${CYAN}Path:${NC}     ${arch_file}"
            echo ""
        fi
    fi
    
    if [ "$BUILD_APPIMAGE" = true ]; then
        local appimage_file="${DIST_DIR}/Open_Media_Converter-${APP_VERSION}-x86_64.AppImage"
        if [ -f "$appimage_file" ]; then
            local appimage_size=$(du -h "$appimage_file" | cut -f1)
            echo -e "${GREEN}✓${NC} AppImage:"
            echo -e "  ${CYAN}File:${NC}     $(basename "$appimage_file")"
            echo -e "  ${CYAN}Size:${NC}     ${appimage_size}"
            echo -e "  ${CYAN}Path:${NC}     ${appimage_file}"
            echo ""
        fi
    fi
    
    # Show timing breakdown
    echo -e "${BOLD}Build Time:${NC}"
    for phase in "${!PHASE_TIMES[@]}"; do
        local duration=$(format_duration "${PHASE_TIMES[$phase]}")
        echo -e "  ${CYAN}$phase:${NC} $duration"
    done
    echo -e "  ${CYAN}Total:${NC} $(format_duration $total_duration)"
    echo ""
    
    # Installation instructions
    echo -e "${BOLD}Installation:${NC}"
    
    if [ "$BUILD_DEB" = true ] && [ -f "${DIST_DIR}/open-media-converter_${APP_VERSION}_amd64.deb" ]; then
        echo ""
        echo -e "${YELLOW}Debian/Ubuntu:${NC}"
        echo "  sudo dpkg -i ${DIST_DIR}/open-media-converter_${APP_VERSION}_amd64.deb"
        echo "  sudo apt-get install -f  # Fix dependencies if needed"
    fi
    
    if [ "$BUILD_RPM" = true ] && [ -f "${DIST_DIR}/open-media-converter-${APP_VERSION}-1.x86_64.rpm" ]; then
        echo ""
        echo -e "${YELLOW}Fedora/RHEL:${NC}"
        echo "  sudo dnf install ${DIST_DIR}/open-media-converter-${APP_VERSION}-1.x86_64.rpm"
        echo "  OR"
        echo "  sudo rpm -ivh ${DIST_DIR}/open-media-converter-${APP_VERSION}-1.x86_64.rpm"
    fi
    
    local arch_file=$(find "${DIST_DIR}" -name "open-media-converter-*.pkg.tar.zst" -type f 2>/dev/null | head -1)
    if [ "$BUILD_ARCH" = true ] && [ -n "$arch_file" ] && [ -f "$arch_file" ]; then
        echo ""
        echo -e "${YELLOW}Arch Linux:${NC}"
        echo "  sudo pacman -U ${arch_file}"
    fi
    
    if [ "$BUILD_APPIMAGE" = true ] && [ -f "${DIST_DIR}/Open_Media_Converter-${APP_VERSION}-x86_64.AppImage" ]; then
        echo ""
        echo -e "${YELLOW}AppImage (Universal):${NC}"
        echo "  chmod +x ${DIST_DIR}/Open_Media_Converter-${APP_VERSION}-x86_64.AppImage"
        echo "  ./Open_Media_Converter-${APP_VERSION}-x86_64.AppImage"
    fi
    
    echo ""
    
    # Distribution notes
    echo -e "${BOLD}Distribution:${NC}"
    echo "  All packages are ready for distribution from the ${CYAN}dist/${NC} directory."
    echo "  DEB:      For Debian/Ubuntu users via package managers"
    echo "  RPM:      For Fedora/RHEL/CentOS users via package managers"
    echo "  Arch:     For Arch Linux users via pacman"
    echo "  AppImage: For portable, universal Linux distribution"
    echo ""
}

# Main build process
main() {
    log_banner "${APP_NAME} - Package Builder"
    
    echo -e "${BOLD}Build Configuration:${NC}"
    echo -e "  DEB Package:       ${BUILD_DEB}"
    echo -e "  RPM Package:       ${BUILD_RPM}"
    echo -e "  Arch Package:      ${BUILD_ARCH}"
    echo -e "  AppImage:          ${BUILD_APPIMAGE}"
    echo -e "  Skip Maven Build:  ${SKIP_BUILD}"
    echo -e "  Embedded Tools:    $( [ "$NO_EMBEDDED_TOOLS" = true ] && echo "false" || echo "true" )"
    echo -e "  Clean Build:       ${CLEAN_BUILD}"
    
    check_prerequisites
    clean_builds
    ensure_dist_clean
    build_jar
    build_deb
    build_rpm
    build_arch
    build_appimage
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}  ${GREEN}✓ All Packages Built Successfully${NC}                      ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    
    show_summary
}

# Run main function
main "$@"
