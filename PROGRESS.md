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
## Phase 7: Collapsible Fixed Notifications [PENDING]
## Phase 8: Setup UI + Polish [PENDING]
