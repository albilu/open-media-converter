# Advanced features

Version: 0.1.0-SNAPSHOT

Last updated: 2025-11-08

---

This page covers power-user capabilities and customization.

Batch processing and parallelism

- Configure concurrency in **Settings → Concurrency**.
- The app uses a bounded thread pool for conversions.
- Increase concurrency only when CPU and disk I/O allow it.

Per-file overrides and presets

- Create presets in **Settings → Presets**.
- Apply presets via the context menu to selected files.
- Per-file overrides affect only the file, not the saved preset.

Image transformations

- Image processing uses ImageMagick.
- Transformation order: rotate → flip → quality → resize.
- To rotate an image, open file details and set **Rotate: 90°**.

Audio/video options

- Video and audio conversions use FFmpeg.
- To copy audio without re-encoding, enable **Audio copy** (`-c:a copy`).
- Set codec and bitrate in your presets for consistent outputs.

GPU acceleration

- Enable GPU acceleration in **Settings → FFmpeg** if supported.
- Common encoders: `h264_nvenc`, `hevc_nvenc`.
- Verify system drivers and FFmpeg build support the chosen encoder.

Safe deletion and overwrite rules

- Enable **Delete original after success** in **Settings → Files** to remove originals.
- The app never deletes files on failure or cancellation.
- When outputs exist, the app prompts to overwrite or skip.

---

Back: [Basic usage](./basic-usage.md) • Next: [Settings and management](./settings-and-management.md) • Home: [Main index](../user-guide.md)
