#!/usr/bin/env bash
# OverSight Android TV — API Test Script
# Tests all endpoints and parameters against the device.
#
# Usage:
#   ./scripts/test_api.sh [HOST] [PORT]
#
# Defaults to Shield TV at 192.168.24.119:5001.
# Set PAUSE=0 to skip visual-check prompts (visual tests will be marked skipped).

HOST="${1:-192.168.24.119}"
PORT="${2:-5001}"
BASE="http://${HOST}:${PORT}"
PAUSE="${PAUSE:-1}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
DIM='\033[2m'
BOLD='\033[1m'
RESET='\033[0m'

# Test tracking
PASS=0
FAIL=0
SKIP=0
CURRENT_TEST=""
declare -a FAILED_TESTS=()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

section() {
    echo ""
    echo -e "${CYAN}${BOLD}════════════════════════════════════════════════════════${RESET}"
    echo -e "${CYAN}${BOLD}  $1${RESET}"
    echo -e "${CYAN}${BOLD}════════════════════════════════════════════════════════${RESET}"
}

test_name() {
    CURRENT_TEST="$1"
    echo ""
    echo -e "${YELLOW}▶ ${CURRENT_TEST}${RESET}"
}

record_pass() {
    echo -e "  ${GREEN}✓ PASS${RESET}"
    PASS=$((PASS + 1))
}

record_fail() {
    local reason="${1:-}"
    if [[ -n "$reason" ]]; then
        echo -e "  ${RED}✗ FAIL: ${reason}${RESET}"
    else
        echo -e "  ${RED}✗ FAIL${RESET}"
    fi
    FAILED_TESTS+=("${CURRENT_TEST}${reason:+ — ${reason}}")
    FAIL=$((FAIL + 1))
}

record_skip() {
    echo -e "  ${DIM}⊘ SKIP (visual check disabled)${RESET}"
    SKIP=$((SKIP + 1))
}

# Pause and ask user to visually confirm pass or fail.
# Usage: pause_for_visual "What to look for on screen"
pause_for_visual() {
    local description="$1"
    if [[ "$PAUSE" != "1" ]]; then
        record_skip
        return
    fi
    echo -e "  ${BOLD}[Visual] ${description}${RESET}"
    while true; do
        read -r -p "  Result? [p]ass / [f]ail / [s]kip: " reply
        case "${reply,,}" in
            p|pass)  record_pass;  return ;;
            f|fail)  record_fail "visual check failed"; return ;;
            s|skip)  record_skip; return ;;
            *)  echo "  Please enter p, f, or s." ;;
        esac
    done
}

# Run a POST and auto-check the success field.
do_post() {
    local path="$1"
    local body="$2"
    echo "  POST ${path}"
    echo -e "  ${DIM}Body: ${body}${RESET}"
    local response
    response=$(curl -s -X POST "${BASE}${path}" \
        -H "Content-Type: application/json" \
        -d "${body}")
    echo "  Response: ${response}"
    local success
    success=$(echo "$response" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d.get('success','false'))" 2>/dev/null)
    if [[ "$success" == "True" ]]; then
        record_pass
    else
        record_fail "API returned success=false"
    fi
}

# Like do_post but silently ignores failure — used for cleanup where the item
# may have already expired/been removed from state.
do_post_silent() {
    local path="$1"
    local body="$2"
    curl -s -X POST "${BASE}${path}" -H "Content-Type: application/json" -d "${body}" > /dev/null
}

# Like do_post but expects failure (success=false → test passes).
do_post_expect_fail() {
    local path="$1"
    local body="$2"
    echo "  POST ${path} (expect rejection)"
    echo -e "  ${DIM}Body: ${body}${RESET}"
    local response
    response=$(curl -s -X POST "${BASE}${path}" \
        -H "Content-Type: application/json" \
        -d "${body}")
    echo "  Response: ${response}"
    local success
    success=$(echo "$response" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d.get('success','false'))" 2>/dev/null)
    if [[ "$success" == "False" ]]; then
        record_pass
    else
        record_fail "expected rejection but got success=true"
    fi
}

do_get() {
    local path="$1"
    echo "  GET ${path}"
    local response
    response=$(curl -s "${BASE}${path}")
    echo "  Response: ${response}"
    local success
    success=$(echo "$response" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d.get('success','false'))" 2>/dev/null)
    if [[ "$success" == "True" ]]; then
        record_pass
    else
        record_fail "API returned success=false"
    fi
}

# ---------------------------------------------------------------------------
# Verify reachable
# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}OverSight API Test Suite${RESET}"
echo "Target: ${BASE}"
if [[ "$PAUSE" == "1" ]]; then
    echo "Visual checks: ENABLED (you will be prompted to grade each visual test)"
else
    echo "Visual checks: DISABLED (set PAUSE=1 to enable)"
fi
echo ""
if ! curl -s --max-time 5 "${BASE}/info" > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Cannot reach ${BASE} — is the device on and service running?${RESET}"
    exit 1
fi
echo -e "${GREEN}Device reachable.${RESET}"

# ---------------------------------------------------------------------------
# 1. GET /info
# ---------------------------------------------------------------------------
section "1. GET /info"

test_name "Fetch device info"
do_get "/info"

# ---------------------------------------------------------------------------
# 2. POST /set/overlay
# ---------------------------------------------------------------------------
section "2. POST /set/overlay"

test_name "overlayVisibility = 50 (semi-transparent dimmer)"
do_post "/set/overlay" '{"overlayVisibility": 50}'
pause_for_visual "Screen should show a visible dark dimmer over the TV content"

test_name "overlayVisibility = 95 (max dimmer)"
do_post "/set/overlay" '{"overlayVisibility": 95}'
pause_for_visual "Screen should be almost fully blacked out by the dimmer"

test_name "overlayVisibility = 0 (no dimmer)"
do_post "/set/overlay" '{"overlayVisibility": 0}'
pause_for_visual "Dimmer should be completely gone"

test_name "clockOverlayVisibility = 100 (fully visible)"
do_post "/set/overlay" '{"clockOverlayVisibility": 100}'
pause_for_visual "Clock should be fully opaque in the corner"

test_name "clockOverlayVisibility = 50 (semi-transparent)"
do_post "/set/overlay" '{"clockOverlayVisibility": 50}'
pause_for_visual "Clock should be dimmed/translucent"

test_name "clockOverlayVisibility = 0 (hidden)"
do_post "/set/overlay" '{"clockOverlayVisibility": 0}'
pause_for_visual "Clock should be completely hidden"

test_name "clockOverlayVisibility = 100 (restore)"
do_post "/set/overlay" '{"clockOverlayVisibility": 100}'

test_name "hotCorner = top_start"
do_post "/set/overlay" '{"hotCorner": "top_start"}'
pause_for_visual "Clock should move to TOP-LEFT corner"

test_name "hotCorner = bottom_start"
do_post "/set/overlay" '{"hotCorner": "bottom_start"}'
pause_for_visual "Clock should move to BOTTOM-LEFT corner"

test_name "hotCorner = bottom_end"
do_post "/set/overlay" '{"hotCorner": "bottom_end"}'
pause_for_visual "Clock should move to BOTTOM-RIGHT corner"

test_name "hotCorner = top_end (default/restore)"
do_post "/set/overlay" '{"hotCorner": "top_end"}'
pause_for_visual "Clock should move back to TOP-RIGHT corner"

test_name "Invalid hotCorner falls back gracefully (API accepts, defaults to top_end)"
do_post "/set/overlay" '{"hotCorner": "invalid_value"}'

test_name "All overlay fields in one request"
do_post "/set/overlay" '{"overlayVisibility": 0, "clockOverlayVisibility": 100, "hotCorner": "top_end"}'

# ---------------------------------------------------------------------------
# 3. POST /set/notifications
# ---------------------------------------------------------------------------
section "3. POST /set/notifications"

# Create a persistent badge now so the fixed-notification setting tests
# have something visible to show/hide/fade.
SETTINGS_BADGE_ID="settings-test-badge"
test_name "Setup: create badge for fixed notification setting tests"
do_post "/notify_fixed" "{\"id\": \"${SETTINGS_BADGE_ID}\", \"icon\": \"mdi:cog\", \"text\": \"Settings Test\"}"
pause_for_visual "'Settings Test' badge with cog icon appears next to the clock"

test_name "displayNotifications = false (verify popup suppressed)"
do_post "/set/notifications" '{"displayNotifications": false}'
do_post "/notify" '{"title": "Should Be Hidden", "message": "This popup must NOT appear", "duration": 5}'
pause_for_visual "NO popup should have appeared on screen"
sleep 2

test_name "displayNotifications = true (restore)"
do_post "/set/notifications" '{"displayNotifications": true}'

test_name "notificationDuration = 3 (verify short dismiss)"
do_post "/set/notifications" '{"notificationDuration": 3}'
do_post "/notify" '{"title": "Short Duration", "message": "Disappears in ~3 seconds"}'
pause_for_visual "Popup appeared and dismissed after ~3 seconds"
sleep 4

test_name "notificationDuration = 15 (verify long display)"
do_post "/set/notifications" '{"notificationDuration": 15}'
do_post "/notify" '{"title": "Long Duration", "message": "Stays visible for ~15 seconds"}'
pause_for_visual "Popup is still visible — confirm it has NOT dismissed yet (15s total)"
sleep 16

test_name "notificationDuration = 8 (restore default)"
do_post "/set/notifications" '{"notificationDuration": 8}'

test_name "notificationLayoutName = Minimalist"
do_post "/set/notifications" '{"notificationLayoutName": "Minimalist"}'
do_post "/notify" '{"title": "Minimalist", "message": "Compact layout style", "largeIcon": "mdi:bell", "duration": 6}'
pause_for_visual "Popup uses Minimalist layout: text-only (no icon, no source), smaller and narrower than Default"
sleep 7

test_name "notificationLayoutName = Only Icon"
do_post "/set/notifications" '{"notificationLayoutName": "Only Icon"}'
do_post "/notify" '{"title": "Icon Only", "message": "Only the icon should show", "largeIcon": "mdi:bell", "duration": 6}'
pause_for_visual "Popup shows only the bell icon — no title or message text"
sleep 7

test_name "notificationLayoutName = Default (restore)"
do_post "/set/notifications" '{"notificationLayoutName": "Default"}'

test_name "displayFixedNotifications = false"
do_post "/set/notifications" '{"displayFixedNotifications": false}'
pause_for_visual "'Settings Test' badge next to the clock should now be hidden"

test_name "displayFixedNotifications = true (restore)"
do_post "/set/notifications" '{"displayFixedNotifications": true}'
pause_for_visual "'Settings Test' badge should reappear"

test_name "fixedNotificationsVisibility = 50"
do_post "/set/notifications" '{"fixedNotificationsVisibility": 50}'
pause_for_visual "'Settings Test' badge appears semi-transparent/dimmed"

test_name "fixedNotificationsVisibility = 100 (restore)"
do_post "/set/notifications" '{"fixedNotificationsVisibility": 100}'

test_name "All notification fields in one request"
do_post "/set/notifications" '{"displayNotifications": true, "notificationLayoutName": "Default", "notificationDuration": 8, "displayFixedNotifications": true, "fixedNotificationsVisibility": 100}'

# Clean up the settings test badge
CURRENT_TEST="Cleanup: remove settings test badge"
do_post "/notify_fixed" "{\"id\": \"${SETTINGS_BADGE_ID}\", \"visible\": false}"

# ---------------------------------------------------------------------------
# 4. POST /set/settings
# ---------------------------------------------------------------------------
section "4. POST /set/settings"

test_name "deviceName change"
do_post "/set/settings" '{"deviceName": "Test Shield TV"}'

test_name "deviceName restore"
do_post "/set/settings" '{"deviceName": "OverSight"}'

test_name "displayDebug = true"
do_post "/set/settings" '{"displayDebug": true}'
echo "  (API-only: no debug UI implemented yet)"

test_name "displayDebug = false"
do_post "/set/settings" '{"displayDebug": false}'

test_name "pixelShift = true"
do_post "/set/settings" '{"pixelShift": true}'
pause_for_visual "Over next 60s the overlay should subtly shift position to prevent burn-in"

test_name "pixelShift = false"
do_post "/set/settings" '{"pixelShift": false}'

# NOTE: remotePort intentionally omitted — changing it would break the test session.
# Manual test: POST {"remotePort": 5001} to verify it accepts the value.

# ---------------------------------------------------------------------------
# 5. Progress bar color (POST / — notification layout)
# ---------------------------------------------------------------------------
section "5. Progress bar color (POST / — notification layout)"

test_name "progressBarColor = red (#F44336)"
do_post "/" '{"name": "Default", "progressBarColor": "#F44336"}'
do_post "/notify" '{"title": "Red Progress Bar", "message": "Bar should be red", "duration": 6}'
pause_for_visual "Progress bar at bottom of popup is RED"
sleep 7

test_name "progressBarColor = green (#4CAF50)"
do_post "/" '{"name": "Default", "progressBarColor": "#4CAF50"}'
do_post "/notify" '{"title": "Green Progress Bar", "message": "Bar should be green", "duration": 6}'
pause_for_visual "Progress bar at bottom of popup is GREEN"
sleep 7

test_name "progressBarColor = white (#FFFFFF)"
do_post "/" '{"name": "Default", "progressBarColor": "#FFFFFF"}'
do_post "/notify" '{"title": "White Progress Bar", "message": "Bar should be white", "duration": 6}'
pause_for_visual "Progress bar at bottom of popup is WHITE"
sleep 7

test_name "progressBarColor = semi-transparent (#882196F3)"
do_post "/" '{"name": "Default", "progressBarColor": "#882196F3"}'
do_post "/notify" '{"title": "Transparent Progress Bar", "message": "Bar should be translucent blue", "duration": 6}'
pause_for_visual "Progress bar is TRANSLUCENT BLUE (semi-transparent)"
sleep 7

test_name "progressBarColor = default blue (#2196F3) — restore"
do_post "/" '{"name": "Default", "progressBarColor": "#2196F3"}'
do_post "/notify" '{"title": "Default Blue", "message": "Bar should be default blue", "duration": 6}'
pause_for_visual "Progress bar is back to DEFAULT BLUE"
sleep 7

# ---------------------------------------------------------------------------
# 6. POST /notify — Toast popup notifications
# ---------------------------------------------------------------------------
section "6. POST /notify — Toast Notifications"

test_name "Title only"
do_post "/notify" '{"title": "Title Only Test", "duration": 5}'
pause_for_visual "Popup shows a title with no source or message text below it"
sleep 6

test_name "Message only"
do_post "/notify" '{"message": "Message-only notification (no title)", "duration": 5}'
pause_for_visual "Popup shows message text only (no separate title line)"
sleep 6

test_name "Title + message"
do_post "/notify" '{"title": "Hello", "message": "This is the message body", "duration": 5}'
pause_for_visual "Popup shows bold title above message text"
sleep 6

test_name "Title + source + message"
do_post "/notify" '{"title": "Motion Detected", "source": "Home Assistant", "message": "Front door camera triggered", "duration": 6}'
pause_for_visual "Popup shows small 'Home Assistant' source above bold title above message"
sleep 7

test_name "duration override (3 seconds)"
do_post "/notify" '{"title": "Short Popup", "message": "Disappears in 3 seconds", "duration": 3}'
pause_for_visual "Popup disappears after ~3 seconds"
sleep 4

test_name "corner = top_start (while hot corner is top_end)"
do_post "/notify" '{"title": "Top Left", "message": "Custom corner override", "corner": "top_start", "duration": 5}'
pause_for_visual "Popup appears in TOP-LEFT despite hot corner being top_end"
sleep 6

test_name "corner = bottom_start"
do_post "/notify" '{"title": "Bottom Left", "message": "Bottom-left corner", "corner": "bottom_start", "duration": 5}'
pause_for_visual "Popup appears in BOTTOM-LEFT corner"
sleep 6

test_name "corner = bottom_end"
do_post "/notify" '{"title": "Bottom Right", "message": "Bottom-right corner", "corner": "bottom_end", "duration": 5}'
pause_for_visual "Popup appears in BOTTOM-RIGHT corner"
sleep 6

test_name "smallIcon standalone (no largeIcon) — circle badge in icon slot"
do_post "/notify" '{"title": "Small Icon Only", "message": "Bell shown as standalone circle badge", "smallIcon": "mdi:bell", "duration": 6}'
pause_for_visual "Bell icon appears in a dark semi-transparent circle in the icon slot (NOT full-size)"
sleep 7

test_name "smallIconColor = #FF5733 (orange-red)"
do_post "/notify" '{"title": "Colored Small Icon", "message": "Bell badge should be orange-red", "smallIcon": "mdi:bell", "smallIconColor": "#FF5733", "duration": 6}'
pause_for_visual "Bell circle badge is orange-red"
sleep 7

test_name "largeIcon (MDI) + smallIcon badge overlay"
do_post "/notify" '{"title": "Large + Badge", "message": "Home icon with bell badge overlay", "largeIcon": "mdi:home", "smallIcon": "mdi:bell", "duration": 6}'
pause_for_visual "Home icon on the left, bell circle badge overlaid on its bottom-right corner"
sleep 7

test_name "largeIcon (MDI) + smallIcon badge overlay with color"
do_post "/notify" '{"title": "Badge with Color", "message": "Home icon with green bell badge", "largeIcon": "mdi:home", "smallIcon": "mdi:bell", "smallIconColor": "#4CAF50", "duration": 6}'
pause_for_visual "Home icon on the left, green bell circle badge on its bottom-right corner"
sleep 7

test_name "largeIcon (URL image)"
do_post "/notify" '{"title": "URL Icon", "message": "Icon loaded from URL", "largeIcon": "https://dummyimage.com/100x100/2196f3/fff.png", "duration": 8}'
pause_for_visual "Blue square image loaded from URL in the icon slot"
sleep 9

test_name "largeIcon (URL image) + smallIcon badge overlay"
do_post "/notify" '{"title": "URL + Badge", "message": "URL image with bell badge overlay", "largeIcon": "https://dummyimage.com/100x100/2196f3/fff.png", "smallIcon": "mdi:bell", "duration": 8}'
pause_for_visual "Blue square image on the left, bell circle badge overlaid on its bottom-right corner"
sleep 9

test_name "image URL (appears below text)"
do_post "/notify" '{"title": "Image Notification", "message": "Photo appears below text", "image": "https://dummyimage.com/400x200/333333/ffffff.png&text=Test+Image", "duration": 10}'
pause_for_visual "Popup shows text row, then a dark grey image scaled to popup width below it"
sleep 11

test_name "video (RTSP)"
do_post "/notify" '{"title": "Camera Feed", "message": "Driveway camera (RTSP via TCP)", "video": "rtsp://192.168.26.2:8554/driveway_sub", "duration": 15}'
pause_for_visual "Popup shows text row, then live video feed below it (muted)"
sleep 16

test_name "Empty notification rejected (no title/message/icon/image)"
do_post_expect_fail "/notify" '{}'

test_name "smallIcon with invalid MDI name rejected"
do_post_expect_fail "/notify" '{"title": "Test", "smallIcon": "mdi:not-a-real-icon-xyz"}'

test_name "smallIcon with non-mdi value rejected (must be mdi: only)"
do_post_expect_fail "/notify" '{"title": "Test", "smallIcon": "https://example.com/icon.png"}'

test_name "largeIcon with invalid MDI name rejected"
do_post_expect_fail "/notify" '{"title": "Test", "largeIcon": "mdi:not-a-real-icon-xyz"}'

test_name "largeIcon with invalid prefix rejected (not mdi: or http(s)://)"
do_post_expect_fail "/notify" '{"title": "Test", "largeIcon": "invalid-value"}'

# ---------------------------------------------------------------------------
# 6. POST /notify_fixed — Fixed notification badges
# ---------------------------------------------------------------------------
section "7. POST /notify_fixed — Fixed Notifications"

FN_ID="test-badge-$(date +%s)"

test_name "Create basic fixed notification (icon + text)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"icon\": \"mdi:bell\", \"text\": \"Alert\"}"
pause_for_visual "Badge with bell icon and 'Alert' text appears next to the clock"

test_name "Partial update — text only (icon preserved)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"text\": \"Updated!\"}"
pause_for_visual "Badge text changes to 'Updated!' while icon stays as bell"

test_name "Partial update — icon only (text preserved)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"icon\": \"mdi:home\"}"
pause_for_visual "Badge icon changes to home, text still shows 'Updated!'"

test_name "iconColor = #FF5733 (orange-red)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"iconColor\": \"#FF5733\"}"
pause_for_visual "Badge icon is now orange-red"

test_name "messageColor = #00FF00 (green)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"messageColor\": \"#00FF00\"}"
pause_for_visual "Badge text is now green"

test_name "backgroundColor = #1A237E (dark blue)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"backgroundColor\": \"#1A237E\"}"
pause_for_visual "Badge background is now dark blue"

test_name "borderColor = #FFD700 (gold)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"borderColor\": \"#FFD700\"}"
pause_for_visual "Badge has a visible gold border"

test_name "shape = circle"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"shape\": \"circle\"}"
pause_for_visual "Badge is circular"

test_name "shape = rectangular"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"shape\": \"rectangular\"}"
pause_for_visual "Badge has sharp rectangular corners"

test_name "shape = rounded (restore default)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"shape\": \"rounded\"}"

test_name "size = small"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"size\": \"small\"}"
pause_for_visual "Badge is noticeably smaller"

test_name "size = big"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"size\": \"big\"}"
pause_for_visual "Badge is noticeably larger"

test_name "size = normal (restore)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"size\": \"normal\"}"

test_name "visible = false (hide badge with exit animation)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"visible\": false}"
pause_for_visual "Badge animates out and disappears"

test_name "visible = true (show badge with enter animation)"
do_post "/notify_fixed" "{\"id\": \"${FN_ID}\", \"visible\": true}"
pause_for_visual "Badge animates back in"

test_name "expirationEpoch (30 seconds from now)"
EXPIRE_EPOCH=$(( $(date +%s) * 1000 + 30000 ))
do_post "/notify_fixed" "{\"id\": \"expire-test\", \"icon\": \"mdi:timer\", \"text\": \"Expires 30s\", \"expirationEpoch\": ${EXPIRE_EPOCH}}"
pause_for_visual "'Expires 30s' badge appeared — wait 30s then verify it auto-removes"

test_name "expiration string (1m)"
do_post "/notify_fixed" "{\"id\": \"expire-str-test\", \"icon\": \"mdi:clock\", \"text\": \"Expires 1m\", \"expiration\": \"1m\"}"
pause_for_visual "'Expires 1m' badge appeared — will auto-remove in 1 minute"

test_name "Collapse: showDuration=5 only (collapses once, stays collapsed)"
do_post "/notify_fixed" "{\"id\": \"collapse-test\", \"icon\": \"mdi:alert\", \"text\": \"Collapses in 5s\", \"showDuration\": 5}"
pause_for_visual "Badge shows text for 5s then collapses to icon-only permanently"
sleep 7

test_name "Collapse: showDuration=4, collapseDuration=3, repeatExpand=true (cycling)"
do_post "/notify_fixed" "{\"id\": \"collapse-repeat\", \"icon\": \"mdi:sync\", \"text\": \"Cycling badge\", \"showDuration\": 4, \"collapseDuration\": 3, \"repeatExpand\": true}"
pause_for_visual "Badge cycles: 4s expanded with text, 3s collapsed to icon-only, repeat"
sleep 14

test_name "Multiple simultaneous badges"
do_post "/notify_fixed" '{"id": "multi-1", "icon": "mdi:home", "text": "Home"}'
do_post "/notify_fixed" '{"id": "multi-2", "icon": "mdi:car", "text": "Car"}'
do_post "/notify_fixed" '{"id": "multi-3", "icon": "mdi:weather-sunny", "text": "Weather"}'
pause_for_visual "Three badges appear side-by-side next to the clock"

test_name "Empty new badge rejected (no icon or text)"
do_post_expect_fail "/notify_fixed" '{"id": "empty-test"}'

test_name "Invalid MDI icon name rejected"
do_post_expect_fail "/notify_fixed" '{"id": "invalid-icon-test", "icon": "mdi:not-a-real-icon-xyz", "text": "Test"}'

test_name "Icon with invalid prefix rejected (not mdi: or http(s)://)"
do_post_expect_fail "/notify_fixed" '{"id": "invalid-icon-test", "icon": "invalid-value", "text": "Test"}'

# ---------------------------------------------------------------------------
# 7. GET /fixed_notifications
# ---------------------------------------------------------------------------
section "8. GET /fixed_notifications"

test_name "List active fixed notifications"
do_get "/fixed_notifications"

# ---------------------------------------------------------------------------
# 8. POST /screen_on
# ---------------------------------------------------------------------------
section "9. POST /screen_on"

test_name "Wake screen"
do_post "/screen_on" '{}'
pause_for_visual "If screen was off/dim, it should now be on"

# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------
section "Cleanup — hiding all test badges"
# Use do_post_silent for badges that may have already auto-expired and been
# removed from state (expire-test, expire-str-test). Hiding a non-existent
# badge with no icon/text is correctly rejected by the API — not a bug.
for badge_id in "$FN_ID" "collapse-test" "collapse-repeat" "multi-1" "multi-2" "multi-3"; do
    CURRENT_TEST="Cleanup: hide ${badge_id}"
    do_post "/notify_fixed" "{\"id\": \"${badge_id}\", \"visible\": false}"
done
for badge_id in "expire-test" "expire-str-test"; do
    echo -e "\n  ${DIM}Cleanup (silent): hide ${badge_id}${RESET}"
    do_post_silent "/notify_fixed" "{\"id\": \"${badge_id}\", \"visible\": false}"
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
section "Test Summary"
TOTAL=$((PASS + FAIL + SKIP))
echo ""
printf "  %-10s %s\n" "Passed:"  "${PASS}"
printf "  %-10s %s\n" "Failed:"  "${FAIL}"
printf "  %-10s %s\n" "Skipped:" "${SKIP}"
printf "  %-10s %s\n" "Total:"   "${TOTAL}"
echo ""

if [[ "${#FAILED_TESTS[@]}" -gt 0 ]]; then
    echo -e "  ${RED}${BOLD}Failed tests:${RESET}"
    for i in "${!FAILED_TESTS[@]}"; do
        echo -e "  ${RED}  $((i + 1)). ${FAILED_TESTS[$i]}${RESET}"
    done
    echo ""
    echo -e "  ${RED}${BOLD}${FAIL} test(s) failed — share the list above to investigate.${RESET}"
else
    echo -e "  ${GREEN}${BOLD}All tests passed!${RESET}"
fi
echo ""

# NOTE: /restart_service is intentionally excluded from automated testing.
# Calling it mid-script would drop the connection and abort remaining tests.
# Manual test: curl -X POST http://HOST:PORT/restart_service
