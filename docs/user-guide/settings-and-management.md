# Settings and management

Version: 0.1.0-SNAPSHOT

Last updated: 2025-11-08

---

This page explains global settings, preset management and persistence.

Global settings overview

- **Output folder**: default destination for converted files.
- **Concurrency**: controls parallel conversions.
- **Tool Binaries**: choose bundled or system tools.
- **Logging**: set verbosity and log file location.

Bundled binaries vs system tools

- Bundled binaries live under `resources/bin/` in the package.
- The app prefers bundled binaries when present.
- Switch to system-installed tools in **Settings → Tool Binaries**.

Preset management

1. Open **Settings → Presets**.
2. Click **New** to create a preset.
3. Configure codecs, bitrates, resolutions and transforms.
4. Save the preset and apply it to files.

Session and state persistence

- The app persists window geometry, presets and sort state to JSON.
- You can restore the last session with pending jobs on restart.

Logging and diagnostics

- Open logs via **Help → View Logs**.
- Logs help when reporting issues to support.

---

Back: [Advanced features](./advanced-features.md) • Next: [Integrations](./integrations.md) • Home: [Main index](../user-guide.md)
