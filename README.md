# Open Media Converter

A fully featured GUI (GTK 4 via java-gi) Linux app to convert video, audio, image and document formats using (FFmpeg, ImageMagick, Pandoc, LibreOffice).

img:resources/omc.png|center|600px

## Quick highlights

-   Multi-format conversions: video, audio, image, documents
-   Batch processing with per-file progress and presets
-   Portable AppImage or native DEB package
-   Bundles tools for an easy out-of-the-box experience (optional)

## Get the app

-   Releases: https://github.com/albilu/open-media-converter/releases

Quick examples:

-   Run the AppImage:

    ```bash
    chmod +x Open_Media_Converter-*.AppImage
    ./Open_Media_Converter-*.AppImage
    ```

-   Install DEB on Debian/Ubuntu:

    ```bash
    sudo dpkg -i open-media-converter_*.deb
    sudo apt-get install -f
    ```

## Build from source (short)

Prerequisites: Java 23+, Maven 3.8+, GTK 4 (for system runtime if not using AppImage).

Docker Build, test, dev, and run:

```bash
make compile
make test
make dev
make run
```

See `scripts/` and `packaging/` for packaging helpers (AppImage / DEB).

## Usage overview

1. Add files or folders in the UI.
2. Select or create a preset in **Settings**.
3. Click **Convert** to start a batch.
4. Monitor per-file progress and open the output folder when finished.

## Troubleshooting & docs

Short troubleshooting is in [`docs/troubleshooting.md`](docs/troubleshooting.md). For more details see the [`docs/`](docs/) folder.

If you hit a problem, enable debug logging and check logs:

```bash
# GUI/AppImage
OMC_DEBUG=1 ./Open_Media_Converter-*.AppImage
# Logs
~/.local/share/open-media-converter/logs/
```

## Contributing

Contributions are welcome. Please:

1. Open an issue describing the change or bug.
2. Fork the repo and create a branch for your work.
3. Run tests: `mvn test` and keep changes small and focused.
