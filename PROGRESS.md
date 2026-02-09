# OverSight - Development Progress

## Phase 1: Project Skeleton + Data Models [COMPLETE]
- Created Gradle project structure with Kotlin DSL
- Generated Gradle 8.9 wrapper via Docker android-build-box
- AndroidManifest.xml with all required permissions and components
- Placeholder adaptive icon (eye + notification badge design)
- All data models with @Serializable:
  - InfoValues with merge() method (no MQTT section)
  - FixedNotification with @JsonNames compat ("message"/"title" -> text, "textColor" -> messageColor)
  - ReceivedNotification (18 fields)
  - OverlayCustomization, NotificationLayout, NotificationLayoutList, NotificationTextFormat
  - All enums: HotCorner, FixedNotificationShape, FixedNotificationSize, FontWeightEnum, PermissionState, BatteryOptimizationState
  - ApiResponse wrapper
- Utility classes: ExpirationParser, ColorParser, NetworkUtils, IconResolver
- Stub classes: OverSightApplication, SetupActivity, OverlayService, BootReceiver, ScreenStateReceiver
- Build verified: `./gradlew assembleDebug` succeeds in Docker

## Phase 2: Core Service + Overlay Window [COMPLETE]
- OverSightApplication with ProcessLifecycleOwner
- OverlayService: foreground service with notification channel, wake lock, screen state tracking
- OverlayWindowManager: ComposeView in TYPE_APPLICATION_OVERLAY with custom LifecycleOwner
- BootReceiver: auto-start on BOOT_COMPLETED/QUICKBOOT_POWERON/REBOOT
- ScreenStateReceiver: SCREEN_ON/OFF tracking
- SetupActivity: Leanback launcher entry, overlay permission request, battery optimization exemption
- OverlayStateStore: central StateFlow-based reactive state management
- Deployed and verified on Shield TV

## Phase 3: REST API Server [COMPLETE]
- Ktor/Netty embedded HTTP server on configurable port (default 5001)
- All endpoints implemented and tested via curl:
  - POST /notify - toast notifications with queue logic
  - POST /notify_fixed - fixed notification upsert with partial update merge
  - GET /fixed_notifications - list active fixed notifications
  - POST /set/overlay, /set/notifications, /set/settings - config merge
  - GET/POST /overlay_customization - clock appearance
  - POST /screen_on - wake lock screen activation
  - POST /restart_service - service restart
  - GET/POST / and /{filter?} - notification layout management
- JSON config: ignoreUnknownKeys, isLenient, encodeDefaults, coerceInputValues

## Phase 4: Compose Overlay UI [COMPLETE]
- OverlayContent: layered layout with background dimmer, clock+fixed row, toast notifications
- Layout: clock at hot corner, fixed notifications stack horizontally from clock, toast stacks vertically
- Per-notification corner override support (toast `corner` field)
- ClockOverlay: follows system 12/24h preference, customizable font/color/shadow
- BackgroundDimmer: 0-95% opacity full-screen overlay
- NotificationPopup: expandIn/shrinkOut animation from corner, auto-dismiss with configurable duration
- NotificationLayouts: Default (icon+source+title+message), Minimalist, Icon Only
- FixedNotificationBadge: shape/size/color, animated enter (expandHorizontally) and exit (shrinkHorizontally)
- MDI icon rendering via mikepenz Iconics library (IconicsDrawable on Compose Canvas)
- Image support via Coil, video support via Media3/ExoPlayer
- Fixed notification partial update API: only ID required for updates, fields merge with existing
- SetupActivity: live permission status updates on resume, battery optimization status display
- All verified on Shield TV with staggered notification tests

## Phase 5: Persistence [COMPLETE]
- PersistenceManager using DataStore Preferences
- Persists: InfoValues, OverlayCustomization, ClockTextFormat, FixedNotifications, NotificationLayoutList, DeviceId
- JSON serialization for complex objects into string preferences
- Auto-save via StateFlow.drop(1).debounce(500).collectLatest observers
- Loads all state on service startup before starting overlay and HTTP server
- Filters expired fixed notifications on load
- Generates and persists a UUID device ID
- Wired into OverlayService: loadAll() in serviceScope, startAutoSave() after load
- Verified on Shield TV: fixed notifications and overlay customization survive service restart
## Phase 6: Zeroconf Discovery [COMPLETE]
- ZeroconfAdvertiser using Android NsdManager for mDNS advertisement
- Service type: `_tvoverlay._tcp`
- TXT records: deviceName, port, version, deviceId
- Starts in OverlayService after HTTP server is up
- Stops in OverlayService.onDestroy()
- Auto re-registers when deviceName or port changes via `/set/settings`
- Fresh RegistrationListener per registration to avoid NsdManager race conditions
- Verified on Shield TV: discoverable via avahi-browse, re-registration on name change works
## Phase 7: Collapsible Fixed Notifications [COMPLETE]
- CollapsibleBadge composable with LaunchedEffect timer-based state machine
- showDuration: seconds to show text before collapsing to icon-only
- collapseDuration: seconds to stay collapsed before re-expanding
- repeatExpand: whether to cycle expand/collapse continuously
- animateContentSize() on badge Row for smooth width transitions
- Edge cases: null showDuration = always expanded, empty text = ignore collapse params
- Verified on Shield TV: cycling animation, non-collapsible badges unaffected, icon-only edge case correct
## Phase 8: Setup UI + Polish [COMPLETE]
- First-launch onboarding wizard: Welcome → Overlay Permission → Battery Optimization → Settings
  - Auto-advances when permissions detected on resume
  - Onboarding completion persisted in DataStore via PersistenceManager
  - D-pad friendly with auto-focused primary buttons and visible Skip button styling
- YouTube TV-style split panel settings UI
  - Left panel (28%): scrollable TvLazyColumn menu with section headers
  - Right panel (72%): detail view for selected setting (About, Toggle, Value, Service, Action)
  - Two-panel D-pad focus model: Right/Enter to interact, Left/Back to return
- Setting types:
  - Toggle: stacked On/Off buttons with checkmark indicator
  - Value: edit button → dialog (Selector slide-in, Slider with live preview, TextInput with keyboard)
  - Service control: Start/Stop/Restart with conditional enable state
  - Action: Clear Fixed Notifications button
- Settings wired to OverlayStateStore: changes reflected in overlay in real-time
- About page: service status, mDNS, IP, port, device name, device ID, permissions with grant buttons
- Clock visibility now controls opacity (0-100% alpha) instead of binary on/off
- Pixel shift: random -6 to +6 dp offset every 60s with animated transitions to prevent burn-in
- Focus management: counter-based LaunchedEffect keys for reliable focus restoration, global Back/Left interceptor on right panel
- SetupActivity loads persisted state via local PersistenceManager when service isn't running

## Post-Phase: HA Integration Support
- Added `GET /info` endpoint in SettingsRoutes returning full `InfoValues` + `deviceId` for HA coordinator polling

## Bug Fixes: Notification UI
- Fixed icon-only badge padding: added proportional extra padding when text is hidden (icon-only or collapsed)
- Fixed popup notification layout: image/video now render below the text row inside the shared background, scaled to popup width
- Fixed video playback in overlay windows: replaced SurfaceView (PlayerView) with raw TextureView to avoid full-screen black surface in TYPE_APPLICATION_OVERLAY
- Fixed RTSP video streams: force TCP interleaved transport via RtspMediaSource.setForceUseRtpTcp(true) — many servers reject UDP (461)
- Video notifications play muted (volume = 0f) and auto-cleanup on dismiss
- Notification layouts accept overrideBgColor parameter to avoid double-background when wrapped by media container
