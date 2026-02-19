# OverSight Android TV — Claude Code Instructions

## Build & Deploy

**Build** (run from the `oversight/` directory):
```bash
docker run --rm \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  -v /home/evildog/.android-docker/debug.keystore:/opt/android-sdk/.android/debug.keystore \
  mingc/android-build-box \
  bash -c 'cd /project && ./gradlew clean assembleDebug'
```

**Deploy:**
```bash
docker run --rm --network host \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  mingc/android-build-box \
  bash -c 'adb connect 192.168.24.119:5555 && sleep 2 && adb install -r /project/app/build/outputs/apk/debug/app-debug.apk'
```

**Keystore note:** Must be mounted at BOTH `/root/.android/` (ADB keys) AND `/opt/android-sdk/.android/debug.keystore` (Gradle signing). The persistent keystore lives at `/home/evildog/.android-docker/debug.keystore` (100-year validity).

**Device:** Shield TV at `192.168.24.119:5555`, API on port `5001`.

## Project Structure

```
app/src/main/java/us/bergnet/oversight/
├── data/
│   ├── model/              # Serializable data classes + enums
│   │   └── enums/          # HotCorner, FixedNotificationShape, FixedNotificationSize, FontWeightEnum
│   ├── repository/         # PersistenceManager (DataStore)
│   └── store/              # OverlayStateStore (StateFlow-based global state)
├── discovery/              # ZeroconfAdvertiser (NsdManager mDNS)
├── receiver/               # BootReceiver, ScreenStateReceiver
├── server/
│   ├── HttpServer.kt       # Ktor/Netty embedded server
│   └── routes/             # SettingsRoutes, NotifyRoutes, OverlayRoutes, LayoutRoutes, ControlRoutes
├── service/
│   ├── OverlayService.kt   # Foreground service; owns HttpServer, WindowManager, ZeroconfAdvertiser
│   └── OverlayWindowManager.kt  # ComposeView in TYPE_APPLICATION_OVERLAY with custom LifecycleOwner
├── ui/
│   ├── components/         # MdiIcon (mikepenz Iconics)
│   ├── overlay/            # OverlayContent, ClockOverlay, BackgroundDimmer
│   │   └── notification/   # NotificationPopup, NotificationLayouts, FixedNotificationBadge
│   ├── setup/              # SetupActivity, OnboardingScreen, SetupScreen
│   └── theme/              # OverlayTheme
└── util/                   # ColorParser, ExpirationParser, IconResolver, NetworkUtils
```

## Key Architecture Decisions

### State Management
`OverlayStateStore` is a singleton `object` holding all `MutableStateFlow`s. Compose collects these directly with `collectAsState()`. State changes from the API are applied via `updateInfoValues()` which calls `InfoValues.merge()` — only non-null fields in the incoming object overwrite existing state.

### Overlay Window
`OverlayWindowManager` adds a `ComposeView` as `TYPE_APPLICATION_OVERLAY` using `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`. It creates a custom `LifecycleOwner` and `SavedStateRegistryOwner` (required for Compose in non-Activity contexts), and sets up a `Recomposer` manually.

### Video in Overlay Windows
`SurfaceView` (used by default in ExoPlayer's `PlayerView`) creates its own independent window surface and renders full-screen black in `TYPE_APPLICATION_OVERLAY` windows. Use raw `TextureView` instead — it composites within the normal view hierarchy. ExoPlayer setup (including `prepare()`) must be deferred to `SurfaceTextureListener.onSurfaceTextureAvailable`. RTSP streams require TCP transport: `RtspMediaSource.Factory().setForceUseRtpTcp(true)` — many servers reject UDP with error 461.

### Persistence
`PersistenceManager` uses Jetpack DataStore Preferences with JSON serialization for complex objects. Auto-save is wired via `StateFlow.drop(1).debounce(500).collectLatest` observers. State is loaded before the overlay and HTTP server start.

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

### Compose Animation in Fixed Notifications
Fixed badges use `MutableTransitionState` so items continue animating out after being removed from the source list. `trackedItems` holds `AnimatedFixedItem` wrappers that survive removal until the exit animation completes (`isIdle && !currentState`).

### CollapsibleBadge State Machine
`collapsed` state is keyed on `notification.id`. The `LaunchedEffect` runs a `while(true)` loop cycling expand→collapse→expand. Keys include `showDuration`, `collapseDuration`, and `repeatExpand` so changing these params restarts the timer correctly.

### Notification Queue and `displayNotifications`
`enqueueNotification()` drops notifications immediately when `displayNotifications = false`. This prevents a backlog that would flush on re-enable. Never accumulate notifications while the setting is off.

### Progress Bar Width and `IntrinsicSize.Max`
When a Column child uses `fillMaxWidth(fraction)`, it forces the Column to expand to its maximum constraint (`widthIn(max = X)`), even when the content is much narrower. Apply `Modifier.width(IntrinsicSize.Max)` before `widthIn(max = ...)` to let the Column size to its natural content width, then the bar animates within that. The bar has 0 intrinsic width so it doesn't influence the Column's intrinsic size calculation.

### Video Aspect Ratio
Use `aspectRatio(16f / 9f)` with `fillMaxWidth()` for video containers instead of a fixed height. A fixed height with dynamic width (from `IntrinsicSize.Max`) produces distorted/square video.

### Small Icon (MDI Circle Badge)
`smallIcon` is MDI-only (not a URL). It renders as a dark semi-transparent circle (`Color(0x99000000)`, `CircleShape`) with the MDI icon at 65% of the circle size. When `largeIcon` is also present, the badge is overlaid at `Alignment.TopEnd` of the large icon Box. When standalone (no large icon), it renders in place of the large icon. Use `smallIconColor` for tint.

### JSON Config
`HttpServer.json` is configured with `ignoreUnknownKeys = true`, `isLenient = true`, `encodeDefaults = true`, `coerceInputValues = true`. This allows partial/legacy payloads without errors.

## API Quick Reference

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/info` | Device state + deviceId |
| POST | `/set/overlay` | overlayVisibility, clockOverlayVisibility, hotCorner |
| POST | `/set/notifications` | displayNotifications, notificationLayoutName, notificationDuration, displayFixedNotifications, fixedNotificationsVisibility |
| POST | `/set/settings` | deviceName, remotePort, pixelShift, displayDebug |
| POST | `/notify` | Toast popup (title, message, source, image, video, smallIcon, largeIcon, smallIconColor, corner, duration) |
| POST | `/notify_fixed` | Fixed badge upsert (id, icon, text, colors, shape, size, expiration, collapse params) |
| GET | `/fixed_notifications` | List active fixed notifications |
| GET/POST | `/overlay_customization` | Clock font/color/shadow |
| GET | `/` | List notification layouts |
| POST | `/` | Create/update a layout and set as active |
| POST | `/screen_on` | Acquire temporary wake lock |
| POST | `/restart_service` | Stop and restart the service |

## Enum Values

- **HotCorner:** `top_start`, `top_end`, `bottom_start`, `bottom_end`
- **FixedNotificationShape:** `circle`, `rounded`, `rectangular`
- **FixedNotificationSize:** `small`, `normal`, `big`
- **FontWeightEnum:** `thin`, `light`, `normal`, `medium`, `semibold`, `bold`, `extrabold`, `black`

## Testing

```bash
./scripts/test_api.sh [HOST] [PORT]   # defaults: 192.168.24.119 5001
PAUSE=0 ./scripts/test_api.sh         # API-only, skip visual prompts
```
