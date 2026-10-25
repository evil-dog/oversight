# OverSight Android TV — Claude Code Instructions

## Build & Deploy

**Build image** (`docker/Dockerfile`: Temurin JDK 17 + Android SDK, matching CI). Rebuild after changing SDK versions in the Dockerfile:
```bash
docker build -t oversight-build docker/
```

**Build** (run from the `oversight/` directory):
```bash
docker run --rm \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  -v oversight-gradle-cache:/root/.gradle \
  oversight-build \
  ./gradlew assembleDebug
```

**Build (clean, use only when needed to fix stale output):**
```bash
docker run --rm \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  -v oversight-gradle-cache:/root/.gradle \
  oversight-build \
  ./gradlew clean assembleDebug
```

**Deploy:**
```bash
docker run --rm --network host \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  oversight-build \
  bash -c 'adb connect 192.168.24.119:5555 && sleep 2 && adb install -r /project/app/build/outputs/apk/debug/app-debug.apk'
```

**Keystore note:** `/home/evildog/.android-docker` is mounted at `/root/.android/` and provides both the ADB keys and the persistent `debug.keystore` used for Gradle signing (100-year validity).

**SDK note:** If AGP or a library requires a newer `compileSdk` or build-tools, bump `COMPILE_SDK` / `BUILD_TOOLS` in `docker/Dockerfile` and rebuild the image — packages AGP auto-downloads at build time are lost when the container exits.

**Device:** Shield TV at `192.168.24.119:5555`, API on port `5001`.

## Key Architecture Decisions

### Overlay Window
`OverlayWindowManager` adds a `ComposeView` as `TYPE_APPLICATION_OVERLAY` using `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`. It creates a custom `LifecycleOwner` and `SavedStateRegistryOwner` (required for Compose in non-Activity contexts), and sets up a `Recomposer` manually.

### Video in Overlay Windows
`SurfaceView` (used by default in ExoPlayer's `PlayerView`) creates its own independent window surface and renders full-screen black in `TYPE_APPLICATION_OVERLAY` windows. Use raw `TextureView` instead — it composites within the normal view hierarchy. ExoPlayer setup (including `prepare()`) must be deferred to `SurfaceTextureListener.onSurfaceTextureAvailable`. RTSP streams require TCP transport: `RtspMediaSource.Factory().setForceUseRtpTcp(true)` — many servers reject UDP with error 461.

### Persistence
`PersistenceManager` uses Jetpack DataStore Preferences with JSON serialization for complex objects. `OverSightApplication` owns it: state is loaded **once per process** and a single auto-save (`StateFlow.debounce(500).collectLatest`, no `drop(1)` so changes made before subscription are still saved) runs for the process lifetime. `OverlayService` and `SetupActivity` `await()` `OverSightApplication.stateLoaded` and must not load or start auto-save themselves — a service restart reloading disk would clobber unsaved in-memory changes. Each key loads independently, and a corrupt DataStore file is replaced via `ReplaceFileCorruptionHandler`.

**Built-in layout versioning:** `loadAll()` always resets the built-in layouts (`Default`, `Minimalist`, `Only Icon`) to their current code definitions from `NotificationLayout.ALL_DEFAULTS`, while preserving user-created custom layouts. This ensures changes to built-in defaults take effect after an app update without clearing user data.

### mDNS
`ZeroconfAdvertiser` uses Android `NsdManager`. A fresh `RegistrationListener` is created per registration to avoid NsdManager race conditions. Re-registers automatically when `deviceName` or `port` changes.

### Fixed Notification Partial Updates
`OverlayStateStore.upsertFixedNotification()` merges incoming data into the existing notification via `mergeWith()`. Only `id` is required for updates; all other fields are nullable and keep their existing values when null. Fields with non-null defaults (like `size`) must be made nullable in the data class so the merge pattern works correctly.

## Critical Patterns & Gotchas

### LaunchedEffect Keys
Never set a `Boolean` LaunchedEffect key back to `false` inside the effect — this triggers recomposition which cancels the running coroutine. Use incrementing `Int` counters as keys instead.

### Android TV D-pad Focus
D-pad Enter/OK `KeyUp` can trigger clicks on newly-focused buttons. Use deferred focus transfer (100ms delay via `LaunchedEffect`) for Enter/OK. `DirectionRight` doesn't trigger button clicks so immediate focus is safe.

### Notification Queue and `displayNotifications`
`enqueueNotification()` drops notifications immediately when `displayNotifications = false`. This prevents a backlog that would flush on re-enable. Never accumulate notifications while the setting is off.

### Progress Bar Width and `IntrinsicSize.Max`
When a Column child uses `fillMaxWidth`, it forces the Column to expand to its maximum constraint (`widthIn(max = X)`), even when the content is much narrower. Apply `Modifier.width(IntrinsicSize.Max)` before `widthIn(max = ...)` to let the Column size to its natural content width. The progress bar (`ProgressBar` in `NotificationPopup.kt`) is `fillMaxWidth()` with 0 intrinsic width, and draws its fraction in `drawBehind` — read animated values in the draw phase, not composition, or the whole popup recomposes every frame.

### Video Aspect Ratio
Use `aspectRatio(16f / 9f)` with `fillMaxWidth()` for video containers instead of a fixed height. A fixed height with dynamic width (from `IntrinsicSize.Max`) produces distorted/square video.

### JSON Config
`HttpServer.json` is configured with `ignoreUnknownKeys = true`, `isLenient = true`, `encodeDefaults = true`, `coerceInputValues = true`. This allows partial/legacy payloads without errors.

## API Reference

See `README.md` for the full REST API; routes live in `server/routes/`.

## Testing

```bash
./scripts/test_api.sh [HOST] [PORT]   # defaults: 192.168.24.119 5001
PAUSE=0 ./scripts/test_api.sh         # API-only, skip visual prompts
```

UI snapshots render Compose screens to PNG without a device — see the `ui-snapshots` skill.
