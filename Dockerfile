FROM eclipse-temurin:23-jdk

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
    # Flatpak bundle building and AppStream validation
    flatpak \
    flatpak-builder \
    appstream \
    librsvg2-bin \
    # Utilities
    vim \
    tree \
    && rm -rf /var/lib/apt/lists/*

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
