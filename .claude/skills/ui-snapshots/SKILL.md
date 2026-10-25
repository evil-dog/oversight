---
name: ui-snapshots
description: Render OverSight's Compose UI (overlay, toasts, fixed badges, setup screen) to PNG on the JVM with Robolectric + Roborazzi, no device needed. Use when changing or verifying UI, adding snapshot cases, or debugging how something looks.
---

# UI Snapshots

Compose UI renders to PNG on the JVM via Robolectric + Roborazzi (`app/src/test/java/us/bergnet/oversight/ui/UiSnapshots.kt`). Output: `app/build/outputs/roborazzi/*.png` at Shield resolution (1920x1080 = `w960dp-h540dp-land-xhdpi`). Read the PNGs to verify UI changes visually.
```bash
docker run --rm -v $(pwd):/project -v /home/evildog/.android-docker:/root/.android \
  -v oversight-gradle-cache:/root/.gradle oversight-build ./gradlew recordRoborazziDebug
```
- `OverlayContent` and `SetupScreen` read `OverlayStateStore` directly — set store state in the test before `setContent`. `@Before` resets the singleton.
- Clock is pinned with a quoted-literal format (`"'9:41 PM'"`) so renders are stable.
- Iconics fonts must be registered in `@Before` (`Iconics.init` + `registerFont(CommunityMaterial)`); on device this happens via androidx.startup, which Robolectric doesn't run.
- Images: `FakeImages` (test) installs a Coil loader that answers every URL with a generated picture. The URL picks the shape: `portrait` (3:4), `square`, `tiny` (64px), otherwise 16:9. It reports `MEMORY_CACHE` so Coil skips crossfade, whose animation would otherwise stay at 0% opacity under Robolectric's frozen clock.
- Video: Robolectric has no decoder/GPU, so `TextureView` stays black. Video snapshots verify layout (16:9 box, rounding, progress bar), not frames.
- `FlowRow` silently drops items that don't fit the screen height; split cases across snapshots instead.
- JVM unit tests run with `./gradlew testDebugUnitTest`.
