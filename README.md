# OverSight — Android TV Overlay

An Android TV application that displays a persistent, configurable overlay for clock display, fixed notification badges, and popup toast notifications. Controlled entirely via a local REST API, with automatic network discovery via mDNS.

## Features

- **Clock overlay** — always-on clock with configurable font, color, and opacity
- **Fixed notification badges** — persistent icon+text badges with collapsible animations, color theming, and expiration
- **Toast notifications** — popup notifications with icon, title, message, image, video (including RTSP streams), and a countdown progress bar
- **Background dimmer** — configurable full-screen darkening overlay
- **REST API** — full control from any HTTP client on the local network
- **mDNS discovery** — advertises as `_tvoverlay._tcp` for automatic detection by Home Assistant and other clients
- **Pixel shift** — small periodic position shift to prevent OLED burn-in
- **Setup UI** — YouTube TV-style D-pad-navigable settings screen with onboarding wizard

## Requirements

- Android TV device (tested on NVIDIA Shield TV)
- Android 8.0+ (API 26+)
- "Display over other apps" permission
- Battery optimization exemption (recommended for background reliability)

## Build & Install

Building requires Docker with the `mingc/android-build-box` image.

**Build:**
```bash
docker run --rm \
  -v $(pwd):/project \
  -v ~/.android-docker:/root/.android \
  -v ~/.android-docker/debug.keystore:/opt/android-sdk/.android/debug.keystore \
  -v oversight-gradle-cache:/root/.gradle \
  mingc/android-build-box \
  bash -c 'cd /project && ./gradlew assembleDebug'
```

The named volume `oversight-gradle-cache` persists Gradle dependencies between builds, making incremental builds significantly faster. Use `./gradlew clean assembleDebug` only when you need to force a full rebuild.

**Install via ADB:**
```bash
docker run --rm --network host \
  -v $(pwd):/project \
  -v ~/.android-docker:/root/.android \
  mingc/android-build-box \
  bash -c 'adb connect <DEVICE_IP>:5555 && sleep 2 && adb install -r /project/app/build/outputs/apk/debug/app-debug.apk'
```

Replace `<DEVICE_IP>` with your Android TV's IP address. ADB debugging must be enabled on the device.

## First Launch

1. Open the app from the launcher (it appears as "OverSight")
2. Follow the onboarding wizard to grant the overlay permission and disable battery optimization
3. The overlay service starts automatically and persists across reboots

## REST API

The HTTP server runs on port **5001** by default. All responses use the envelope:
```json
{"success": true, "message": "...", "result": { ... }}
```

### Settings

#### `GET /info`
Returns current device state and `deviceId`.

#### `POST /set/overlay`
```json
{
  "overlayVisibility": 0,
  "clockOverlayVisibility": 100,
  "hotCorner": "top_end"
}
```
- `overlayVisibility` — background dimmer opacity, `0`–`95`
- `clockOverlayVisibility` — clock opacity, `0`–`100`
- `hotCorner` — `top_start` | `top_end` | `bottom_start` | `bottom_end`

#### `POST /set/notifications`
```json
{
  "displayNotifications": true,
  "notificationLayoutName": "Default",
  "notificationDuration": 8,
  "displayFixedNotifications": true,
  "fixedNotificationsVisibility": 100
}
```
- `notificationLayoutName` — `Default` | `Minimalist` | `Only Icon` (or any custom layout name)
- `notificationDuration` — default popup display time in seconds
- `fixedNotificationsVisibility` — badge transparency, `0`–`100` (100 = fully visible)

#### `POST /set/settings`
```json
{
  "deviceName": "Living Room TV",
  "remotePort": 5001,
  "pixelShift": false,
  "displayDebug": false
}
```

### Notifications

#### `POST /notify`
Show a popup toast notification.
```json
{
  "title": "Motion Detected",
  "message": "Front door camera triggered",
  "source": "Home Assistant",
  "largeIcon": "mdi:home",
  "smallIcon": "mdi:bell",
  "smallIconColor": "#FF5733",
  "image": "https://example.com/snapshot.jpg",
  "video": "rtsp://192.168.1.100:8554/camera",
  "corner": "top_end",
  "duration": 8
}
```
- `largeIcon` — MDI icon name (e.g. `mdi:bell`) or image URL for the primary icon
- `smallIcon` — MDI icon name only; rendered as a small dark circle badge. Shown standalone if no `largeIcon`, or overlaid at the top-right of the large icon otherwise
- `smallIconColor` — tint color for the small icon (hex string, e.g. `#FF5733`)
- `corner` — per-notification corner override, independent of `hotCorner`
- `duration` — display time in seconds, overrides global `notificationDuration`
- `video` — supports RTSP (forced TCP), HLS, MP4, and other Media3-compatible URLs

#### `POST /notify_fixed`
Create or update a persistent badge. All fields except `id` are optional for updates — unset fields keep their existing values.
```json
{
  "id": "my-badge",
  "icon": "mdi:alert",
  "text": "Motion",
  "iconColor": "#FF5733",
  "messageColor": "#FFFFFF",
  "backgroundColor": "#CC000000",
  "borderColor": "#FFD700",
  "shape": "rounded",
  "size": "normal",
  "visible": true,
  "expiration": "1h",
  "expirationEpoch": 1700000000000,
  "showDuration": 5,
  "collapseDuration": 10,
  "repeatExpand": true
}
```
- `shape` — `circle` | `rounded` | `rectangular`
- `size` — `small` | `normal` | `big`
- `expiration` — human-readable duration string: `30s`, `5m`, `1h`, `2d`
- `expirationEpoch` — Unix epoch in **milliseconds**; takes priority over `expiration`
- `showDuration` — seconds to show text before collapsing to icon-only
- `collapseDuration` — seconds to stay collapsed before re-expanding (requires `repeatExpand: true`)
- `repeatExpand` — whether to cycle expand/collapse continuously
- `text` also accepts `message` or `title` as aliases
- `messageColor` also accepts `textColor` as an alias

#### `GET /fixed_notifications`
Returns all active (non-expired, visible) fixed notifications.

### Clock Appearance

#### `GET /overlay_customization`
#### `POST /overlay_customization`
```json
{
  "fontSize": 12,
  "fontWeight": "normal",
  "color": "#FFFFFF",
  "displayShadow": true,
  "backgroundColor": "#000000"
}
```
- `fontWeight` — `thin` | `light` | `normal` | `medium` | `semibold` | `bold` | `extrabold` | `black`

### Notification Layouts

#### `GET /` — list all layouts
#### `GET /{filter}` — filter layouts by name
#### `POST /` — create or update a layout (sets it as active)

**Built-in layouts:**

| Layout | Description |
|--------|-------------|
| `Default` | Icon + source + title + message; up to 260dp wide |
| `Minimalist` | Title + message only (no icon, no source); compact 220dp wide |
| `Only Icon` | Large icon with optional small badge; no text |

**Layout fields:** `name`, `imageDisplay`, `titleDisplay`, `sourceDisplay`, `messageDisplay`, `iconDisplay`, `iconSize`, `iconSecondaryDisplay`, `iconSecondarySize`, `maxWidth`, `backgroundColor`, `progressBarColor`, plus `titleFormat`, `sourceFormat`, `messageFormat` (each with `color`, `fontSize`, `fontWeight`, `maxLines`).

- `progressBarColor` — countdown bar color at the bottom of popup notifications (default `#2196F3`)

### Device Control

#### `POST /screen_on`
Acquires a temporary wake lock to turn the screen on.

#### `POST /restart_service`
Stops and restarts the overlay service. The HTTP connection will drop briefly.

## mDNS Discovery

The service advertises itself on the local network:
- **Service type:** `_tvoverlay._tcp`
- **TXT records:** `deviceName`, `port`, `version`, `deviceId`

Compatible clients (e.g. the OverSight Home Assistant integration) discover the device automatically without manual IP configuration.

## Testing

An API test script covers every endpoint and parameter:

```bash
./scripts/test_api.sh [HOST] [PORT]   # defaults: 192.168.24.119 5001
PAUSE=0 ./scripts/test_api.sh         # API-only, skip visual prompts
```

Visual checks prompt for pass/fail (`[p]ass / [f]ail / [s]kip`) and produce a failure summary at the end.

## Architecture

| Layer | Key Files |
|-------|-----------|
| Application | `OverSightApplication.kt` |
| Foreground service | `service/OverlayService.kt` |
| Overlay window | `service/OverlayWindowManager.kt` |
| State management | `data/store/OverlayStateStore.kt` |
| Persistence | `data/repository/PersistenceManager.kt` (DataStore) |
| HTTP server | `server/HttpServer.kt` + `server/routes/` |
| mDNS | `discovery/ZeroconfAdvertiser.kt` |
| Overlay UI | `ui/overlay/OverlayContent.kt` |
| Setup UI | `ui/setup/SetupActivity.kt` |
| Receivers | `receiver/BootReceiver.kt`, `receiver/ScreenStateReceiver.kt` |

## License

MIT — see [LICENSE](LICENSE).
