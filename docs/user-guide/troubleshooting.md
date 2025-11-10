# Troubleshooting

Version: 0.1.0-SNAPSHOT

Last updated: 2025-11-08

---

Common errors and fixes

Error: "Tool not found: ffmpeg"

- Meaning: The app cannot find the `ffmpeg` binary.
- Fix: Install FFmpeg or configure bundled binaries in **Settings → Tool Binaries**.

Error: "Conversion failed: Permission denied"

- Meaning: The app cannot write to the output folder.
- Fix: Choose a writable folder or adjust permissions.

Error: "Out of disk space"

- Meaning: The target volume is full.
- Fix: Free disk space or use a different output folder.

Error: "Unsupported format / conversion not possible"

- Meaning: The chosen tool or codec combination is incompatible.
- Fix: Try another output format or preset.

Diagnostic checklist

1. Open **Help → View Logs** and inspect the log.
2. Verify required external tools are present or bundled.
3. Reproduce the issue with a single small file.
4. Run the equivalent CLI command to compare behavior.

How to collect a reproducible report

1. Note the app version and OS.
2. Attach a small sample input file if possible.
3. Include application logs and a screenshot.
4. Describe the preset and settings used.

---

Back: [Integrations](./integrations.md) • Next: [FAQ](./faq.md) • Home: [Main index](../user-guide.md)
