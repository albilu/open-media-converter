# Desktop baseline without Java, GTK4 or conversion tools. Graphics and font
# libraries reserved for the host by AppImage's excludelist remain available.
ARG DISTRO=22.04
FROM ubuntu:${DISTRO}
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
    xvfb xauth xdotool dbus-x11 fontconfig fonts-dejavu-core \
    libharfbuzz0b libfribidi0 libwayland-client0 libasound2-dev \
    libgbm1 libgl1 libegl1 libgles2 file python3 binutils \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /bin/bash omc-smoke
USER omc-smoke
