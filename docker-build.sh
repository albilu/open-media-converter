#!/bin/bash
# Simple Docker build script for Open Media Converter
set -e

PROJECT_NAME="open-media-converter"
IMAGE_NAME="${OMC_IMAGE_NAME:-omc-dev}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${GREEN}[OMC]${NC} $1"
}

# The image uses the caller's UID/GID, so ordinary private cache permissions work.
prepare_m2() {
    mkdir -p "$HOME/.m2"
}

# Persist the Flatpak user installation (GNOME runtime/sdk downloads) across
# container runs; without this every package run re-downloads ~1GB.
prepare_flatpak_cache() {
    mkdir -p "$HOME/.omc-flatpak"
}

# X11 authentication forwarder. Wayland/Xwayland sessions gate the display
# behind an Xauthority token (e.g. /run/user/*/.mutter-Xwaylandauth.*). Docker
# containers must receive that token or the X server rejects the connection
# with "Authorization required, but no authorization protocol specified".
# The token is copied to a stable host path before each container launch.
xauth_args() {
    if [ -z "$DISPLAY" ] || [ -z "$XAUTHORITY" ] || [ ! -f "$XAUTHORITY" ]; then
        return
    fi
    local host_auth="$HOME/.omc-xauthority"
    if ! cp "$XAUTHORITY" "$host_auth" 2>/dev/null; then
        return
    fi
    echo " -e XAUTHORITY=/tmp/omc-xauthority -v $host_auth:/tmp/omc-xauthority:rw"
}

# Run application
run() {
    prepare_m2
    local xa="$(xauth_args)"
    log "Running application with GUI..."
    docker run --init --rm \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        -e DISPLAY=$DISPLAY \
        $xa \
        -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
        --ipc=host \
        $IMAGE_NAME \
        bash -c 'mvn -q -pl omc-gtk -am package -DskipTests=true && java --enable-native-access=ALL-UNNAMED -jar omc-gtk/target/open-media-converter-*.jar'
}

# Run application in debug mode
debug() {
    prepare_m2
    local xa="$(xauth_args)"
    log "Running application in debug mode (port 5005) with GUI..."
    docker run --init --rm \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        -e DISPLAY=$DISPLAY \
        $xa \
        -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
        -p 5005:5005 \
        --ipc=host \
        $IMAGE_NAME \
        bash -c 'mvn -q -pl omc-gtk -am package -DskipTests=true && java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:5005 --enable-native-access=ALL-UNNAMED -jar omc-gtk/target/open-media-converter-*.jar'
}

# Build the Docker image
build() {
    log "Building Docker image..."
    docker build --build-arg "OMC_UID=$(id -u)" --build-arg "OMC_GID=$(id -g)" -t "$IMAGE_NAME" .
}

# Start development container
dev() {
    prepare_m2
    local xa="$(xauth_args)"
    log "Starting development container..."
    docker run --init -it --rm \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        -e DISPLAY=$DISPLAY \
        $xa \
        -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
        --name omc-dev \
        $IMAGE_NAME
}

# Run the test suite under Xvfb (GUI tests need a display)
test() {
    prepare_m2
    log "Running tests..."
    docker run --init --rm \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        $IMAGE_NAME \
        bash -c "Xvfb :99 -screen 0 1024x768x24 -ac +extension GLX +render -noreset > /dev/null 2>&1 & sleep 2 && mvn test"
}

# Build application
compile() {
    prepare_m2
    log "Building application..."
    docker run --init --rm \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        $IMAGE_NAME \
        mvn clean compile package -DskipTests=true
}

# Create packages
package() {
    prepare_m2
    prepare_flatpak_cache
    local version="${1:-}"
    if [ -n "$version" ] && [[ ! "$version" =~ ^[0-9]+([.][0-9]+){1,3}$ ]]; then
        echo "Invalid package version: expected numeric dotted version" >&2
        return 2
    fi
    log "Creating packages${version:+ (version ${version})}..."
    # --privileged: flatpak-builder's bwrap needs user namespaces and mount
    # propagation control that Docker's default sandbox blocks
    docker run --init --rm \
        --privileged \
        -v "$(pwd):/app" \
        -v "$HOME/.m2:/home/developer/.m2" \
        -v "$HOME/.omc-flatpak:/home/developer/.local/share/flatpak" \
        $IMAGE_NAME \
        /app/packaging/build-packages.sh $version
}

# Clean up
clean() {
    log "Cleaning up..."
    docker rmi $IMAGE_NAME 2>/dev/null || true
    docker system prune -f
}

# Show help
help() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build     Build Docker image"
    echo "  dev       Start development container"
    echo "  test      Run the test suite under Xvfb"
    echo "  compile   Build application"
    echo "  run       Run application with GUI support"
    echo "  debug     Run application in debug mode (port 5005)"
    echo "  package   Create distribution packages (.deb/.rpm/.pkg.tar.zst/.AppImage)"
    echo "  clean     Clean up Docker resources"
    echo "  help      Show this help"
}

# Main
case "${1:-help}" in
    build)   build ;;
    dev)     build && dev ;;
    test)    build && test ;;
    compile) build && compile ;;
    run)     build && run ;;
    debug)   build && debug ;;
    package) shift; build && package "$@" ;;
    clean)   clean ;;
    help)    help ;;
    *)       help ;;
esac
