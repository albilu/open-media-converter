# Integrations

Version: 0.1.0-SNAPSHOT

Last updated: 2025-11-08

---

External tools

- **FFmpeg**: video and audio conversions.
- **ImageMagick (`convert`)**: image transforms and image-to-PDF.
- **Pandoc**: text/document conversions.
- **LibreOffice (soffice)**: office document conversions.

Bundled binaries

- Packaged builds may include binaries under `resources/bin/`.
- The app uses bundled binaries first, then falls back to system tools.

Opening files and folders

- Select a file and click **Open Output Folder** to reveal it.
- Use **Open with default app** to launch the file using the system handler.

Using logs for support

- Use **Help → View Logs** to open the application log file.
- Include logs and a short reproduction when filing issues.

Automated testing and command parity

- If a conversion fails in the app, try the equivalent `ffmpeg` or `convert` CLI command.
- Comparing CLI output can help isolate tool-specific problems.

---

Back: [Settings and management](./settings-and-management.md) • Next: [Troubleshooting](./troubleshooting.md) • Home: [Main index](../user-guide.md)
