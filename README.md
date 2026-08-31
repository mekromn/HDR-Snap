# HDR Snap

HDR Snap is an Android screenshot utility focused on **true HDR preservation**.

## Core rule

HDR Snap never labels an SDR-derived image as a native HDR capture.

Every output records provenance:

- `SYSTEM_HDR_GAINMAP` — genuine Android HDR screenshot with an embedded gainmap produced by the Android screenshot pipeline.
- `SDR_UPCONVERTED` — SDR source to which HDR expansion was intentionally applied by HDR Snap.

SDR upconversion is **off by default**.

## Android 16 strategy

Android 16 can store HDR screenshots as PNG files containing:

- an 8-bit SDR base rendition,
- an 8-bit gainmap,
- ISO 21496-1 gainmap metadata.

HDR Snap watches the system Screenshots collection and, when a genuine gainmapped screenshot appears, produces a JPEG/R Ultra HDR companion while preserving the decoded gainmap. The original gainmapped PNG remains the archival master.

### Fidelity policy

For a genuine HDR screenshot HDR Snap must not:

- synthesize a replacement gainmap,
- tone-map HDR to SDR and then rebuild HDR,
- alter gainmap ratios/gamma,
- resize the source or gainmap,
- discard the original Android HDR PNG.

JPEG/R companions are encoded at quality 100. The native Android gainmapped PNG is retained as the highest-fidelity archive.

## Capture workflow

1. Enable HDR Snap's accessibility screenshot service.
2. Grant photo access so HDR Snap can detect newly-created screenshots.
3. Use the HDR Snap Quick Settings tile, the in-app Capture button, or the normal Android screenshot shortcut.
4. Android creates the screenshot through the system screenshot pipeline.
5. HDR Snap detects the new screenshot, determines whether it contains a gainmap, and creates the requested companion output.

The accessibility service deliberately invokes Android's global screenshot action instead of using MediaProjection as the primary path. This keeps Android 16's native HDR screenshot pipeline in control of HDR capture.

## Output provenance

For generated JPEG outputs HDR Snap writes provenance into EXIF `UserComment` and `ImageDescription`, then re-opens the file and verifies that the gainmap still decodes. A file that fails gainmap verification is rejected rather than silently saved as Ultra HDR.

Example:

```text
HDRSnapSource=SDR_UPCONVERTED
HDRSnapNativeHDR=false
HDRSnapNotice=HDR gainmap synthesized from an SDR screenshot; not a native HDR capture
```

For genuine HDR:

```text
HDRSnapSource=SYSTEM_HDR_GAINMAP
HDRSnapNativeHDR=true
HDRSnapNotice=Gainmap preserved from Android system HDR screenshot
```

## Status

Initial Android project and first working capture/conversion implementation are being built in this repository.
