# Getting started

Version: 0.1.0-SNAPSHOT

Last updated: 2025-11-08

---

This page explains prerequisites, installation and first-run setup.

Prerequisites

-   Linux (x86_64 or ARM64).
-   OpenJDK 23 or newer if building from source.
-   GTK 4 for system UI libraries.
-   Enough disk space and write permissions for outputs.

Installation — Packaged (recommended)

1. Download the AppImage, DEB or RPM from the releases page.
2. Make the AppImage executable:

`chmod +x OpenMediaConverter.AppImage`

3. Run the AppImage:

`./OpenMediaConverter.AppImage`

If you use DEB or RPM, install with your distro tools.

Installation — From source

1. Clone the repository.
2. Build with Maven:

`mvn clean package`

3. Run the launcher after build:

`./omc-gtk/bin/open-media-converter`

[Screenshot: Application launcher or desktop entry location]

First run and initial setup

1. On first run the app restores default settings.
2. Open **Settings** from the menu to review defaults.
3. Check **Tool Binaries** to see bundled or system tools.

> Note: The app falls back to system-installed tools if bundled binaries are missing.

---

Back: [Overview](./overview.md) • Next: [Basic usage](./basic-usage.md) • Home: [Main index](../user-guide.md)
