# HDR Snap

HDR Snap is an Android screenshot utility focused on **true HDR preservation and automatic gainmapped screenshot replacement**.

## Provenance rule

HDR Snap never labels an SDR-derived image as a native HDR capture.

- `SYSTEM_HDR_GAINMAP` — genuine Android HDR screenshot with an embedded gainmap produced by Android's screenshot pipeline.
- `SDR_UPCONVERTED` — SDR source to which HDR expansion was intentionally applied by HDR Snap.

SDR-derived outputs include both EXIF provenance and `SDR-UPCONVERTED` in the filename.

## Automatic workflow

After one-time setup, normal Android screenshots require no HDR Snap interaction:

1. Take a screenshot normally (for example Power + Volume Down).
2. HDR Snap observes the new item in MediaStore.
3. A short grace period allows the Pixel screenshot preview's Edit action to be used.
4. If Pixel Studio or legacy Markup opens, processing is suspended for the editing session.
5. After the final edited media item settles, HDR Snap inspects its gainmap.
6. Native HDR keeps the Android gainmap; SDR can receive a clearly-labelled synthesized gainmap.
7. The final JPEG/R is written to the normal `Pictures/Screenshots` folder at quality 100.
8. HDR Snap reopens that output and requires `Bitmap.hasGainmap() == true`.
9. Only after that verification may superseded source screenshot files be deleted.

If Media Management special access is not granted, HDR Snap keeps the originals rather than prompting or risking data loss.

## Edited screenshots

HDR Snap watches Accessibility `TYPE_WINDOW_STATE_CHANGED` events only to identify screenshot-editing sessions. Known Pixel editors include Pixel Studio (`com.google.android.apps.pixel.creativeassistant`) and legacy Markup (`com.google.android.markup`). If an editor rewrites the source URI, HDR Snap waits for it to settle. If it publishes a new MediaStore row, the new row becomes the source of truth and the older row is treated as superseded, but is still not deleted until the new Ultra HDR replacement passes gainmap verification.

## Capture path

On Android 16, Android's native screenshot pipeline can generate a PNG containing an SDR base plus an ISO 21496-1 gainmap when HDR content is present. HDR Snap deliberately uses the normal system screenshot path and observes its saved output instead of treating an SDR MediaProjection framebuffer as genuine HDR.

## Development signing

CI debug builds use a repository-visible **development-only** signing key so successive test APKs have a stable signature. It must never be used for a production release.
