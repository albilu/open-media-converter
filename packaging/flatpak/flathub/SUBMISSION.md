# Flathub Submission Guide for Open Media Converter

This directory holds the submission-ready manifest for publishing
`io.github.albilu.OpenMediaConverter` on Flathub. The locally/CI-built bundle
uses `../io.github.albilu.OpenMediaConverter.yml` (pre-built jar); Flathub's
build service requires building from source, offline — that is what
`io.github.albilu.OpenMediaConverter.yml` in this directory does.

## App-id

`io.github.albilu.OpenMediaConverter` is a GitHub-based id, which Flathub
accepts without domain ownership proof. Desktop entry, metainfo and icons are
renamed to the app-id by the manifest's build-commands; the deb/rpm/arch
packages keep their existing names.

## Sandboxed converters

- ffmpeg/ffprobe/pandoc: embedded in the shaded jar (archives pre-seeded from
  the `omc-gtk/scripts/tools.json` pins so the offline build succeeds).
- ImageMagick: built from a pinned source tarball (legacy `convert` enabled).
- LibreOffice: intentionally not bundled; office-document conversions are
  unavailable in the flatpak (the app disables them gracefully).

## Per-release checklist

1. Set the pom to the plain release version, commit, tag `vX.Y.Z`, push.
2. In the submission manifest: update `tag: vX.Y.Z` and `commit:` to the tag's
   commit sha.
3. Regenerate `maven-sources.json` (see below) and place it next to the
   submission manifest.
4. Keep the two `.tool-cache` file sources in sync with
   `omc-gtk/scripts/tools.json` (url, sha256, dest-filename = `<sha256>.archive`).
5. Open/refresh the PR against https://github.com/flathub/flathub with the
   manifest + `maven-sources.json` at the repository root.

## Vendoring Maven dependencies (the one manual step)

Flathub builds run without network access, so every Maven artifact must be
declared as a source. Generate the file from a checkout of the release tag:

```bash
git clone https://github.com/flatpak/flatpak-builder-tools
python3 flatpak-builder-tools/maven/flatpak-maven-generator.py \
    /path/to/open-media-converter/pom.xml maven-sources.json
```

Sanity-check the offline build locally before submitting:

```bash
flatpak-builder --force-clean --repo=repo builddir \
    io.github.albilu.OpenMediaConverter.yml
flatpak build-bundle repo omc.flatpak io.github.albilu.OpenMediaConverter
```

## Other Flathub requirements already covered

- AppStream metainfo with description, OARS rating, screenshots and a
  `<launchable>` matching the app-id desktop file (validated in CI by
  `packaging/verify-packages.sh` via `appstreamcli validate`).
- GPL-3.0-or-later license file installed under
  `/app/share/licenses/io.github.albilu.OpenMediaConverter/`.
- Icons in hicolor at every size shipped by the project.
