# AppImage's catalog runs on Ubuntu 22.04 (glibc 2.35). Build its private
# GTK dependency closure there while retaining modern system converters in
# the development/native-package image.
FROM ubuntu:22.04 AS appimage-native
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl build-essential gettext ninja-build python3-pip \
    python3-packaging pkg-config libgtk-4-dev libpcre2-dev libffi-dev \
    libmount-dev libxml2-dev libdrm-dev libtiff-dev librsvg2-common \
    adwaita-icon-theme shared-mime-info patchelf \
    && rm -rf /var/lib/apt/lists/*
RUN python3 -m pip install --no-cache-dir meson==1.4.2
COPY packaging/appimage/build-gtk.sh /tmp/omc-build-gtk.sh
RUN bash /tmp/omc-build-gtk.sh && rm /tmp/omc-build-gtk.sh
ENV PKG_CONFIG_PATH=/opt/omc-gtk/lib/pkgconfig:/opt/omc-gtk/share/pkgconfig
ENV LD_LIBRARY_PATH=/opt/omc-gtk/lib
RUN mkdir -p /opt/omc-appimage && \
    curl -fL --retry 3 https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage -o /opt/omc-appimage/appimagetool && \
    echo 'ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0  /opt/omc-appimage/appimagetool' | sha256sum -c - && \
    chmod 755 /opt/omc-appimage/appimagetool && \
    head -c "$(/opt/omc-appimage/appimagetool --appimage-offset)" /opt/omc-appimage/appimagetool > /opt/omc-appimage/runtime-x86_64 && \
    for name in excludelist appdir-lint.sh; do \
        curl -fL --retry 3 "https://raw.githubusercontent.com/AppImage/AppImages/19e30b276ffedf4d3b4b56bc6320f463625a74f8/$name" -o "/opt/omc-appimage/$name"; \
    done
COPY packaging/appimage/bundle-native.py packaging/appimage/check-abi.py /tmp/omc-appimage/
RUN python3 /tmp/omc-appimage/bundle-native.py /opt/omc-appimage/native && \
    python3 /tmp/omc-appimage/check-abi.py /opt/omc-appimage/native

FROM eclipse-temurin:23-jdk-noble

# Avoid interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install all dependencies
RUN apt-get update && apt-get install -y \
    # Maven (JDK 23 comes from the base image)
    maven \
    # Build tools
    git \
    curl \
    wget \
    python3 \
    # GTK libraries for java-gi (GTK4)
    libgtk-4-dev \
    libglib2.0-dev \
    pkg-config \
    # External conversion tools (matching debian/control)
    ffmpeg \
    imagemagick \
    pandoc \
    libreoffice \
    # Independent PDF content inspection in real-tool regression tests
    poppler-utils \
    # X11 for GUI testing
    xvfb \
    x11-utils \
    dbus-x11 \
    # Package building tools
    dpkg-dev \
    fakeroot \
    rpm \
    file \
    zstd \
    libarchive-tools \
    # Desktop metadata validation and icon rendering
    appstream \
    desktop-file-utils \
    libfile-mimeinfo-perl \
    librsvg2-bin \
    # Utilities
    vim \
    tree \
    && rm -rf /var/lib/apt/lists/*

COPY --from=appimage-native /opt/omc-appimage /opt/omc-appimage

# JAVA_HOME is already set by the temurin base image

# Match the checkout owner, including CI runners whose UID is not 1000.
ARG OMC_UID=1000
ARG OMC_GID=1000
RUN if [ "$OMC_UID" != 0 ]; then \
        existing_user="$(getent passwd "$OMC_UID" | cut -d: -f1)"; \
        if [ -n "$existing_user" ]; then userdel "$existing_user"; fi; \
        if ! getent group "$OMC_GID" >/dev/null; then groupadd -g "$OMC_GID" developer; fi; \
        useradd -m -d /home/developer -s /bin/bash -u "$OMC_UID" -g "$OMC_GID" developer; \
    else mkdir -p /home/developer; fi && \
    mkdir -p /app /home/developer/.m2 && \
    chown -R "$OMC_UID:$OMC_GID" /app /home/developer

WORKDIR /app
# Explicit Java home keeps the Maven cache consistent for root callers too.
ENV MAVEN_OPTS="-Duser.home=/home/developer"
USER ${OMC_UID}:${OMC_GID}

# Set up display for GUI testing
ENV DISPLAY=:99

# Default command
CMD ["/bin/bash"]
