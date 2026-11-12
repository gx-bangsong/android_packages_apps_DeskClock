#!/usr/bin/env bash
#
# Emulator scenario matrix for the alarm / workday / holiday integration.
# Invoked by .github/workflows/alarm-test.yml inside reactivecircus/android-emulator-runner.
#
# Firing is asserted from logcat (AlarmKlaxon / alarms.AlarmActivity) and dumpsys, never
# from audio hardware: the emulator runs headless and may have no sound device.
#
# Scenarios (see the PR description for the full matrix):
#   A  plain alarm, no workday logic                 -> must FIRE
#   B  statutory workday type + EMPTY holiday data   -> must FIRE (empty data never suppresses)
#   C  third-party data URL (fixture server) + type  -> data loads, alarm must FIRE
#   D  statutory type + today is a legal holiday     -> must NOT fire (skip works)
#   E  statutory type + today is a make-up workday
#      and the weekly pattern EXCLUDES today         -> must FIRE (comp day = workday)
#
# The app under test is the debug build (package com.android.deskclock.dev). Every scenario
# starts from `pm clear`, so holiday data is empty unless the scenario injects it.

set -uo pipefail

PKG="com.android.deskclock.dev"
APK="${APK:-build/outputs/apk/debug/DeskClock-debug.apk}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES="$HERE/fixtures"
LOGS="$HERE/logs"
mkdir -p "$FIXTURES" "$LOGS"

RESULTS=()
LAST_AM=""

note() { echo "[alarm-test] $*"; }

annotate_error() { echo "::error::alarm-test: $*"; }

# Annotations are the only output channel readable through the API from restricted
# networks, so every failure path funnels its evidence into them.
crash_report() {
    local crashes
    crashes="$(adb logcat -d 2>/dev/null | grep -E 'FATAL EXCEPTION|Process: com\.android\.deskclock' | head -n 6 | tr '\n' '|' | cut -c1-500)"
    if [ -n "$crashes" ]; then
        annotate_error "app crash detected: $crashes"
    fi
}

logcat_deskclock_tail() {
    adb logcat -d 2>/dev/null | grep -i "deskclock" | tail -n 15 | tr '\n' '|' | cut -c1-700
}

ui_texts_summary() {
    python3 "$HERE/ui.py" texts 2>/dev/null | head -n 20 | tr '\n' '|' | cut -c1-600
}

pass_scenario() { RESULTS+=("PASS $1"); echo "::notice::alarm-test PASS: $1"; note "PASS: $1"; }
fail_scenario() { RESULTS+=("FAIL $1: $2"); echo "::error::alarm-test FAIL: $1 - $2"; note "FAIL: $1 - $2"; }

dev_epoch() { adb shell date +%s | tr -d '\r\n'; }
dev_tz() { adb shell getprop persist.sys.timezone | tr -d '\r\n'; }

# dev_date_field <epoch> <format-char>  (H, M, F=date, u=ISO weekday 1..7)
dev_date_field() {
    local tz
    tz="$(dev_tz)"
    [ -n "$tz" ] || tz=UTC
    TZ="$tz" date -d "@$1" "+%$2"
}

launch_app() { adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
stop_app() { adb shell am force-stop "$PKG" >/dev/null 2>&1; }
clear_app() { adb shell pm clear "$PKG" >/dev/null 2>&1; }

wake() {
    adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
    adb shell wm dismiss-keyguard >/dev/null 2>&1
}

ui() { python3 "$HERE/ui.py" "$@"; }

# create_alarm <hour> <minute> [calendar-day-ints csv, e.g. "2,3,4,5,6" = Mon-Fri]
create_alarm() {
    local h="$1" m="$2" days="${3:-}"
    local args=(shell am start -a android.intent.action.SET_ALARM
        --ei android.provider.AlarmClock.EXTRA_HOUR "$h"
        --ei android.provider.AlarmClock.EXTRA_MINUTES "$m"
        --ez android.provider.AlarmClock.EXTRA_SKIP_UI true)
    if [ -n "$days" ]; then
        args+=(--eia android.provider.AlarmClock.EXTRA_DAYS "$days")
    fi
    LAST_AM="$(adb "${args[@]}" 2>&1 | tr -d '\r')"
    note "am start output: ${LAST_AM:0:300}"
}

# HandleApiCalls logs "Created new alarm" on success; without it the intent did not
# produce an alarm and every later assertion is meaningless.
verify_alarm_created() {
    if logcat_wait_for 'Created new alarm' 45; then
        note "alarm creation confirmed in logcat"
        return 0
    fi
    annotate_error "alarm NOT created. am output: ${LAST_AM:0:400}"
    annotate_error "deskclock logcat tail: $(logcat_deskclock_tail)"
    crash_report
    return 1
}

# Expand the (only) alarm, set workday type to "Statutory workdays". This exercises the
# real UI save path: WorkdayTypeActivity.saveAlarm() -> rescheduleAlarm().
set_statutory_via_ui() {
    launch_app
    sleep 4
    if ! ui exists --rid arrow --timeout 10 --no-scroll; then
        # Maybe a different tab is selected; try the alarms tab in the bottom bar.
        ui tap --contains "Alarm" --timeout 10 --no-scroll >/dev/null 2>&1 || true
        sleep 2
    fi
    if ! ui tap --rid arrow --timeout 30; then
        annotate_error "alarm row expand (id/arrow) not found; UI now: $(ui_texts_summary)"
        crash_report
        return 1
    fi
    if ! ui tap --text "Workday type" --timeout 25; then
        annotate_error "Workday type row not found after expand; UI now: $(ui_texts_summary)"
        return 1
    fi
    if ! ui tap --text "Statutory workdays" --timeout 25; then
        annotate_error "Statutory workdays option not found; UI now: $(ui_texts_summary)"
        return 1
    fi
    sleep 5
    return 0
}

fired() {
    if adb logcat -d 2>/dev/null | grep -qE 'AlarmKlaxon|alarms\.AlarmActivity'; then
        return 0
    fi
    if adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -q 'alarms\.AlarmActivity'; then
        return 0
    fi
    return 1
}

wait_fired() { # <timeout-seconds>
    local deadline=$(( $(date +%s) + $1 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if fired; then
            return 0
        fi
        sleep 5
    done
    fired
}

logcat_wait_for() { # <pattern> <timeout-seconds>
    local deadline=$(( $(date +%s) + $2 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if adb logcat -d 2>/dev/null | grep -qE "$1"; then
            return 0
        fi
        sleep 3
    done
    adb logcat -d 2>/dev/null | grep -qE "$1"
}

snapshot() { # <label>
    adb logcat -d >"$LOGS/$1.logcat.txt" 2>/dev/null
    adb exec-out screencap -p >"$LOGS/$1.png" 2>/dev/null
    adb shell dumpsys alarm >"$LOGS/$1.dumpsys-alarm.txt" 2>/dev/null
    adb shell dumpsys activity activities >"$LOGS/$1.dumpsys-activity.txt" 2>/dev/null
}

# Inserts holiday rows into the app database while the app is stopped. Uses on-device
# sqlite3 when available, otherwise pulls, patches and pushes the database via run-as
# (the debug build is debuggable, so run-as works without root).
inject_holiday_row() { # <name> <start> <end> <comp-days-json>
    local sql="INSERT INTO holiday (name, start_date, end_date, comp_days) VALUES ('$1', '$2', '$3', '$4');"
    if [ "$(adb shell 'command -v sqlite3' 2>/dev/null | tr -d '\r')" != "" ]; then
        adb shell run-as "$PKG" sqlite3 databases/alarms.db "$sql"
        return $?
    fi
    local tmp
    tmp="$(mktemp -d)"
    adb shell run-as "$PKG" cat databases/alarms.db >"$tmp/alarms.db" 2>/dev/null
    sqlite3 "$tmp/alarms.db" "$sql" || { rm -rf "$tmp"; return 1; }
    adb push "$tmp/alarms.db" /data/local/tmp/alarms.db >/dev/null || { rm -rf "$tmp"; return 1; }
    adb shell run-as "$PKG" sh -c \
        'rm -f databases/alarms.db-wal databases/alarms.db-shm; cp /data/local/tmp/alarms.db databases/alarms.db' \
        || { rm -rf "$tmp"; return 1; }
    rm -rf "$tmp"
}

# Writes the holiday_data_url preference into device-protected storage BEFORE the app
# ever starts, so HolidayRepository reads it on first init. SettingsActivity is not
# exported, so the URL cannot be typed through am; only the "Update holiday data"
# preference tap is done through the UI.
set_holiday_url_pref() { # <url>
    local tmp
    tmp="$(mktemp -d)"
    cat >"$tmp/prefs.xml" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="holiday_data_url">$1</string>
</map>
XML
    adb push "$tmp/prefs.xml" /data/local/tmp/alarm-test-prefs.xml >/dev/null
    adb shell run-as "$PKG" sh -c \
        "mkdir -p /data/user_de/0/$PKG/shared_prefs && cp /data/local/tmp/alarm-test-prefs.xml /data/user_de/0/$PKG/shared_prefs/${PKG}_preferences.xml"
    rm -rf "$tmp"
}

update_holiday_data_via_ui() {
    launch_app
    sleep 4
    ui tap --desc "Settings" --timeout 8 --no-scroll || {
        ui tap --desc "More options" --timeout 15 || return 1
        ui tap --text "Settings" --timeout 15 || return 1
    }
    ui tap --text "Update holiday data" --timeout 30 || return 1
    logcat_wait_for 'Holiday data updated' 60
}

# --- scenarios -----------------------------------------------------------------

scenario_a() {
    note "=== A: plain alarm, no workday logic -> must fire ==="
    clear_app
    sleep 2
    wake
    local target h m
    target=$(( $(dev_epoch) + 120 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    create_alarm "$h" "$m" ""
    if ! verify_alarm_created; then
        fail_scenario "A plain alarm" "alarm was not created by the SET_ALARM intent"
        snapshot scenario-a
        return
    fi
    adb logcat -c
    if wait_fired 300; then
        pass_scenario "A plain alarm fired"
    else
        fail_scenario "A plain alarm" "did not fire within window"
        annotate_error "A no-fire; deskclock logcat: $(logcat_deskclock_tail)"
        crash_report
    fi
    snapshot scenario-a
}

scenario_b() {
    note "=== B: statutory type + EMPTY holiday data -> must fire ==="
    clear_app
    sleep 2
    wake
    local target h m
    target=$(( $(dev_epoch) + 270 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    create_alarm "$h" "$m" "1,2,3,4,5,6,7"
    if ! verify_alarm_created; then
        fail_scenario "B statutory+empty" "alarm was not created by the SET_ALARM intent"
        snapshot scenario-b
        return
    fi
    if ! set_statutory_via_ui; then
        fail_scenario "B statutory+empty" "UI automation failed"
        snapshot scenario-b
        return
    fi
    adb logcat -c
    if wait_fired 430; then
        pass_scenario "B statutory type with empty dataset fired"
    else
        fail_scenario "B statutory+empty dataset" "did not fire; an empty dataset must never suppress alarms"
        annotate_error "B no-fire; deskclock logcat: $(logcat_deskclock_tail)"
        crash_report
    fi
    snapshot scenario-b
}

scenario_c() {
    note "=== C: third-party data URL -> data loads + statutory alarm fires ==="
    clear_app
    sleep 2
    wake
    local future_start future_end
    future_start="$(date -u -d '+30 days' +%F)"
    future_end="$(date -u -d '+32 days' +%F)"
    cat >"$FIXTURES/holiday_c.json" <<JSON
[{"name": "FixtureFutureHoliday", "startDate": "$future_start", "endDate": "$future_end", "compDays": []}]
JSON
    set_holiday_url_pref "http://10.0.2.2:8080/holiday_c.json"
    local target h m
    target=$(( $(dev_epoch) + 270 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    create_alarm "$h" "$m" "1,2,3,4,5,6,7"
    if ! verify_alarm_created; then
        fail_scenario "C third-party source" "alarm was not created by the SET_ALARM intent"
        snapshot scenario-c
        return
    fi
    if ! update_holiday_data_via_ui; then
        fail_scenario "C third-party source" "data update through the UI failed"
        annotate_error "C settings UI state: $(ui_texts_summary)"
        snapshot scenario-c
        return
    fi
    adb shell input keyevent BACK >/dev/null 2>&1
    sleep 2
    if ! set_statutory_via_ui; then
        fail_scenario "C third-party source" "UI automation failed"
        snapshot scenario-c
        return
    fi
    adb logcat -c
    if wait_fired 430; then
        pass_scenario "C third-party data source loaded and alarm fired"
    else
        fail_scenario "C third-party source" "data loaded but alarm did not fire"
        annotate_error "C no-fire; deskclock logcat: $(logcat_deskclock_tail)"
        crash_report
    fi
    snapshot scenario-c
}

scenario_d() {
    note "=== D: statutory type + today is a legal holiday -> must NOT fire ==="
    clear_app
    sleep 2
    wake
    launch_app
    sleep 6
    stop_app
    local target tdate h m
    target=$(( $(dev_epoch) + 270 ))
    tdate="$(dev_date_field "$target" F)"
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    if ! inject_holiday_row "FixtureHolidayD" "$tdate" "$tdate" "[]"; then
        fail_scenario "D legal holiday" "could not inject holiday row"
        snapshot scenario-d
        return
    fi
    launch_app
    sleep 4
    create_alarm "$h" "$m" "1,2,3,4,5,6,7"
    if ! verify_alarm_created; then
        fail_scenario "D legal holiday" "alarm was not created by the SET_ALARM intent"
        snapshot scenario-d
        return
    fi
    if ! set_statutory_via_ui; then
        fail_scenario "D legal holiday" "UI automation failed"
        snapshot scenario-d
        return
    fi
    adb logcat -c
    if wait_fired 430; then
        fail_scenario "D legal holiday" "alarm FIRED although today is a legal holiday"
    else
        pass_scenario "D legal holiday skipped (no ring)"
    fi
    snapshot scenario-d
}

scenario_e() {
    note "=== E: make-up workday today, weekly pattern excludes today -> must fire ==="
    clear_app
    sleep 2
    wake
    launch_app
    sleep 6
    stop_app
    local target tdate iso_dow cal_dow hol_start hol_end h m days d
    target=$(( $(dev_epoch) + 270 ))
    tdate="$(dev_date_field "$target" F)"
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    iso_dow="$(dev_date_field "$target" u)"
    # Calendar day-of-week int: ISO 1..7 (Mon..Sun) -> Calendar 2..7,1
    cal_dow=$(( iso_dow == 7 ? 1 : iso_dow + 1 ))
    # Weekly pattern covers every day EXCEPT today's weekday: only the make-up day
    # logic can make the alarm ring today.
    days=""
    for d in 1 2 3 4 5 6 7; do
        if [ "$d" -ne "$cal_dow" ]; then
            days+="${days:+,}$d"
        fi
    done
    # A holiday next week whose make-up workday is today.
    hol_start="$(dev_date_field $(( target + 5 * 86400 )) F)"
    hol_end="$(dev_date_field $(( target + 6 * 86400 )) F)"
    if ! inject_holiday_row "FixtureHolidayE" "$hol_start" "$hol_end" "[\"$tdate\"]"; then
        fail_scenario "E make-up workday" "could not inject holiday row"
        snapshot scenario-e
        return
    fi
    launch_app
    sleep 4
    create_alarm "$h" "$m" "$days"
    if ! verify_alarm_created; then
        fail_scenario "E make-up workday" "alarm was not created by the SET_ALARM intent"
        snapshot scenario-e
        return
    fi
    if ! set_statutory_via_ui; then
        fail_scenario "E make-up workday" "UI automation failed"
        snapshot scenario-e
        return
    fi
    adb logcat -c
    if wait_fired 430; then
        pass_scenario "E make-up workday fired"
    else
        fail_scenario "E make-up workday" "did not fire; the weekly pattern skipped the make-up day (PR #2 regression)"
    fi
    snapshot scenario-e
}

main() {
    note "installing $APK"
    if ! adb install -r "$APK"; then
        echo "::error::adb install failed"
        exit 1
    fi
    if ! adb shell pm list packages 2>/dev/null | tr -d '' | grep -q "$PKG"; then
        annotate_error "package $PKG missing after install; installed packages mentioning deskclock:"
        annotate_error "$(adb shell pm list packages 2>/dev/null | tr -d '' | grep -i deskclock | tr '
' ' ')"
        exit 1
    fi
    wake
    launch_app
    sleep 5
    if [ -z "$(adb shell pidof "$PKG" 2>/dev/null | tr -d '
')" ]; then
        annotate_error "app process not running after launch; it may be crashing"
        crash_report
        annotate_error "deskclock logcat tail: $(logcat_deskclock_tail)"
    fi
    scenario_a
    scenario_b
    scenario_c
    scenario_d
    scenario_e

    echo
    note "==== scenario summary ===="
    local r failed=0
    for r in ${RESULTS[@]+"${RESULTS[@]}"}; do
        echo "$r"
        case "$r" in
            FAIL*) failed=$((failed + 1)) ;;
        esac
    done
    if [ "$failed" -gt 0 ]; then
        echo "::error::${failed} alarm-test scenario(s) failed (logs and screenshots under scripts/alarm-test/logs)"
        exit 1
    fi
    note "all scenarios passed"
}

main "$@"
