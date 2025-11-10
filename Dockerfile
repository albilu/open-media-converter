# Ubuntu build stage for Open Media Converter
FROM ubuntu:24.04 AS builder

# Set environment variables to avoid interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies first
RUN apt-get update && apt-get install -y \
    wget \
    apt-transport-https \
    gnupg \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Add Adoptium repository for Java 23
RUN wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium-archive-keyring.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

# Install build dependencies including Java 23
RUN apt-get update && apt-get install -y \
    temurin-23-jdk \
    maven \
    dpkg-dev \
    rpm \
    tar \
    gzip \
    file \
    libgtk-4-dev \
    libgirepository1.0-dev \
    gobject-introspection \
    sudo \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/temurin-23-jdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create a user with configurable UID/GID (defaults to 1000:1000)
ARG USER_ID=1000
ARG GROUP_ID=1000
RUN (groupadd -g ${GROUP_ID} builder 2>/dev/null || groupmod -n builder $(getent group ${GROUP_ID} | cut -d: -f1)) && \
    useradd -m -u ${USER_ID} -g builder -s /bin/bash builder 2>/dev/null || usermod -l builder -d /home/builder -m $(getent passwd ${USER_ID} | cut -d: -f1) && \
    echo "builder ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Set working directory
WORKDIR /workspace

# Copy project files
COPY . .

# Change ownership to builder user
RUN chown -R builder:builder /workspace

# Switch to builder user
USER builder

# Development stage - includes all build tools and can run tests
FROM builder AS dev

# Switch back to root to install additional packages
USER root

# Install additional dev tools and conversion tools
RUN apt-get update && apt-get install -y \
    git \
    vim \
    less \
    ffmpeg \
    imagemagick \
    pandoc \
    libreoffice \
    && rm -rf /var/lib/apt/lists/*

# Switch back to builder user
USER builder

# Default command for dev environment
CMD ["/bin/bash"]

# Arch Linux stage - for building Arch packages
FROM archlinux:latest AS arch-builder

# Install build dependencies for Arch package
RUN pacman -Syu --noconfirm && \
    pacman -S --noconfirm \
    base-devel \
    jdk-openjdk \
    maven \
    git \
    gtk4 \
    gobject-introspection \
    namcap \
    && pacman -Scc --noconfirm

# Set JAVA_HOME for Arch
ENV JAVA_HOME=/usr/lib/jvm/default
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create a non-root user for building (makepkg refuses to run as root)
ARG USER_ID=1000
ARG GROUP_ID=1000
RUN groupadd -g ${GROUP_ID} builder 2>/dev/null || groupmod -n builder $(getent group ${GROUP_ID} | cut -d: -f1) && \
    useradd -m -u ${USER_ID} -g builder -s /bin/bash builder 2>/dev/null || usermod -l builder -d /home/builder -m $(getent passwd ${USER_ID} | cut -d: -f1)

# Set working directory
WORKDIR /workspace

# Copy project files
COPY . .

# Change ownership to builder user
RUN chown -R builder:builder /workspace

# Switch to builder user
USER builder

# Runtime stage - minimal image for running the application
FROM ubuntu:24.04 AS runtime

# Set environment variables to avoid interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies first
RUN apt-get update && apt-get install -y \
    wget \
    apt-transport-https \
    gnupg \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Add Adoptium repository for Java 23
RUN wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium-archive-keyring.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

# Install runtime dependencies including Java 23
RUN apt-get update && apt-get install -y \
    temurin-23-jre \
    libgtk-4-1 \
    libgirepository-1.0-1 \
    gobject-introspection \
    ffmpeg \
    imagemagick \
    pandoc \
    libreoffice \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/temurin-23-jre-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create application directory
RUN mkdir -p /opt/open-media-converter

# Copy built JAR from builder
COPY --from=builder /workspace/omc-gtk/target/open-media-converter-*.jar /opt/open-media-converter/app.jar

# Set working directory
WORKDIR /opt/open-media-converter

# Run the application
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
