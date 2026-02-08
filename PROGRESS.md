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

## Phase 2: Core Service + Overlay Window [PENDING]
## Phase 3: REST API Server [PENDING]
## Phase 4: Compose Overlay UI [PENDING]
## Phase 5: Persistence [PENDING]
## Phase 6: Zeroconf Discovery [PENDING]
## Phase 7: Collapsible Fixed Notifications [PENDING]
## Phase 8: Setup UI + Polish [PENDING]
