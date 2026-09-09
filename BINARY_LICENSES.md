# Embedded conversion tools

Release JARs contain separate command-line executables. Versions, archive URLs,
SHA-256 checksums, and source references are recorded in `bin/tools.json` and each
tool's `SOURCE.json`. Open Media Converter runs these tools as external processes.

* FFmpeg and ffprobe: the pinned BtbN GPL build enables GPL and version-3 components,
  including libx264 and libx265. Its GPL license text is included beside the binaries.
  [FFmpeg sources](https://github.com/FFmpeg/FFmpeg) and
  [build recipes and dependency sources](https://github.com/BtbN/FFmpeg-Builds)
  describe the upstream components; the exact build reference is in `SOURCE.json`.
* Pandoc: GPL version 2 or later. `COPYING` is included beside its executable.
  [Pandoc 3.10.2 sources](https://github.com/jgm/pandoc/tree/3.10.2).

The application's source license is in [LICENSE](LICENSE).
ImageMagick and LibreOffice are discovered on the system and are not embedded in the JAR.
