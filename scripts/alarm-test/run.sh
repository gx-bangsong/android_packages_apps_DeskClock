#!/usr/bin/env bash
#
# Emulator scenario matrix for the alarm / workday / holiday integration.
# Invoked by .github/workflows/alarm-test.yml inside reactivecircus/android-emulator-runner.
#
# Firing is asserted from logcat (AlarmKlaxon / alarms.AlarmActivity) and dumpsys, never
# from audio hardware: the emulator runs headless with -noaudio.
#
# Scenarios (see the PR description for the full matrix):
#   A  plain alarm, no workday logic                 -> must FIRE
#   B  statutory workday type + EMPTY holiday data   -> must FIRE (empty data never suppresses)
#   C  third-party data URL (fixture server) + type  -> data loads, alarm must FIRE
#   D  statutory type + today is a legal holiday     -> must NOT fire (skip works)
#   E  statutory type + today is a make-up workday
#      and the weekly pattern EXCLUDES today         -> must FIRE (comp day = workday)
#
# Alarm creation: every scenario inserts the alarm row directly into the app database
# (via run-as on the debuggable build) and toggles the alarm on through the real UI
# switch, which runs the production asyncUpdateAlarm -> scheduling path. The public
# SET_ALARM intent is deliberately not used: with the stock sample alarms present it
# just enables the first one at ITS default time (no search extras = match-all), so it
# cannot place an alarm at a controlled time. Annotations carry all failure evidence
# because workflow log downloads may be blocked from restricted networks.

set -uo pipefail

# Bash exits silently on unbound variables and similar errors; surface the crash site
# through an annotation because workflow log downloads may be unavailable.
trap 'rc=$?; if [ "$rc" -ne 0 ]; then echo "::error::alarm-test aborted unexpectedly (rc=$rc, last command: $BASH_COMMAND)"; fi' EXIT

PKG="com.android.deskclock.dev"
APK="${APK:-build/outputs/apk/debug/DeskClock-debug.apk}"
# ClockDatabaseHelper opens the database through a device-protected storage context,
# so it lives under /data/user_de (NOT the /data/user dir that run-as starts in).
DB_PATH="/data/user_de/0/$PKG/databases/alarms.db"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES="$HERE/fixtures"
LOGS="$HERE/logs"
mkdir -p "$FIXTURES" "$LOGS"

RESULTS=()

note() { echo "[alarm-test] $*"; }

annotate_error() { echo "::error::alarm-test: $*"; }

crash_report() {
    local crashes
    crashes="$(adb logcat -d 2>/dev/null | grep -E 'FATAL EXCEPTION|Process: com\.android\.deskclock' | head -n 6 | tr '\n' '|' | cut -c1-500)"
    if [ -n "$crashes" ]; then
        annotate_error "app crash detected: $crashes"
    fi
}

logcat_app_tail() {
    adb logcat -d 2>/dev/null | grep -iE "deskclock|AlarmStateManager|AlarmService|AlarmKlaxon|HandleApiCalls|WorkdayAlarmScheduler|HolidayRepository|AlarmUpdateHandler" | tail -n 15 | tr '\n' '|' | cut -c1-700
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

# --- alarm creation: database insert + real UI toggle ---------------------------

# Calendar day-of-week int (Mon=2..Sat=7, Sun=1) -> Weekdays bit (Mon=0x01..Sat=0x20, Sun=0x40)
cal_to_bit() {
    case "$1" in
        1) echo 64 ;;
        2) echo 1 ;;
        3) echo 2 ;;
        4) echo 4 ;;
        5) echo 8 ;;
        6) echo 16 ;;
        7) echo 32 ;;
    esac
}

db_exec() { # <sql>  (app must be stopped)
    # The SQL travels in a file and is executed via sqlite3's .read command: passing it
    # as an adb shell argument mangles the quotes (adb joins arguments and the device
    # shell re-splits them, producing "incomplete input" errors).
    local out
    if ! adb shell run-as "$PKG" ls "$DB_PATH" >/dev/null 2>&1; then
        annotate_error "database missing at $DB_PATH; dir contents: $(adb shell run-as "$PKG" ls /data/user_de/0/"$PKG"/databases/ 2>&1 | tr '\r\n' ' ')"
        return 1
    fi
    printf '%s\n' "$1" >/tmp/alarm-test.sql
    if ! adb push /tmp/alarm-test.sql /data/local/tmp/alarm-test.sql >/dev/null 2>&1; then
        annotate_error "pushing the sql file failed"
        return 1
    fi
    out="$(adb shell "run-as $PKG sqlite3 $DB_PATH '.read /data/local/tmp/alarm-test.sql'" 2>&1 | tr -d '\r')"
    if printf '%s' "$out" | grep -qi 'error\|denied\|not found'; then
        annotate_error "sqlite3 .read failed: ${out:0:400}"
        return 1
    fi
    return 0
}

# insert_alarm_db <hour> <minute> <weekdays-bits> <workday-type-int> [extra-sql]
# The app must have run once (so the database exists) and be stopped now.
insert_alarm_db() {
    local h="$1" m="$2" bits="$3" type="$4" extra="${5:-}"
    db_exec "DELETE FROM alarm_instances; DELETE FROM alarm_templates; $extra
INSERT INTO alarm_templates (hour, minutes, daysofweek, enabled, vibrate, label, ringtone,
                             delete_after_use, incvol, workday_type,
                             shift_cycle_days, shift_start_date, shift_skip_holiday,
                             shift_days_mask, shift_times)
VALUES ($h, $m, $bits, 0, 1, 'alarm-test', 'content://settings/system/alarm_sound',
        0, 0, $type, 7, '', 0, '', '');"
}

# Launches the app and turns the (single) alarm on through the real UI; the toggle runs
# the production asyncUpdateAlarm path that creates and registers the instance.
toggle_alarm_on_via_ui() {
    launch_app
    sleep 4
    if ! ui exists --rid onoff --timeout 15; then
        annotate_error "alarm on/off switch not found; UI now: $(ui_texts_summary)"
        crash_report
        return 1
    fi
    ui tap --rid onoff --timeout 15 || return 1
    sleep 5
    return 0
}

verify_scheduled() {
    local deadline=$(( $(date +%s) + 30 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        local count
        count="$(adb shell dumpsys alarm 2>/dev/null | tr -d '\r' | grep -c "$PKG")"
        if [ "${count:-0}" -gt 0 ]; then
            note "alarm scheduled (AlarmManager shows $count entries for the app)"
            return 0
        fi
        sleep 3
    done
    annotate_error "no AlarmManager entries for the app; app logcat: $(logcat_app_tail)"
    crash_report
    return 1
}

# --- holiday data ---------------------------------------------------------------

# Writes the holiday_data_url preference into device-protected storage BEFORE the app
# ever starts, so HolidayRepository reads it on first init.
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
        ui tap --desc "More options" --timeout 15 || {
            annotate_error "overflow menu not found; UI now: $(ui_texts_summary)"
            return 1
        }
        ui tap --text "Settings" --timeout 15 || {
            annotate_error "Settings menu item not found; UI now: $(ui_texts_summary)"
            return 1
        }
    }
    ui tap --text "Update holiday data" --timeout 30 || {
        annotate_error "Update holiday data preference not found; UI now: $(ui_texts_summary)"
        return 1
    }
    logcat_wait_for 'Holiday data updated' 60
}

# --- scenarios ------------------------------------------------------------------

scenario_a() {
    note "=== A: plain alarm, no workday logic -> must fire ==="
    local target h m
    target=$(( $(dev_epoch) + 180 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    # Non-repeating one-shot alarm (daysofweek 0), no workday type.
    prepare_alarm_db "$h" "$m" 0 0 || {
        fail_scenario "A plain alarm" "database setup failed"
        snapshot scenario-a
        return
    }
    toggle_alarm_on_via_ui || {
        fail_scenario "A plain alarm" "UI toggle failed"
        snapshot scenario-a
        return
    }
    verify_scheduled || {
        fail_scenario "A plain alarm" "alarm not scheduled after toggle"
        snapshot scenario-a
        return
    }
    adb logcat -c
    if wait_fired 300; then
        pass_scenario "A plain alarm fired"
    else
        fail_scenario "A plain alarm" "did not fire within window"
        annotate_error "A no-fire; app logcat: $(logcat_app_tail)"
        crash_report
    fi
    snapshot scenario-a
}

# prepare_alarm_db <hour> <minute> <bits> <type> [extra-sql]
# Common B-E setup: clear, wake, run the app once so the database exists, stop it,
# then inject the alarm row (plus optional holiday rows) while the database is cold.
prepare_alarm_db() {
    clear_app
    sleep 2
    wake
    launch_app
    sleep 6
    stop_app
    sleep 1
    insert_alarm_db "$1" "$2" "$3" "$4" "${5:-}"
}

scenario_b() {
    note "=== B: statutory type + EMPTY holiday data -> must fire ==="
    local target h m
    target=$(( $(dev_epoch) + 240 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    prepare_alarm_db "$h" "$m" 127 1 || {
        fail_scenario "B statutory+empty" "database setup failed"
        snapshot scenario-b
        return
    }
    toggle_alarm_on_via_ui || {
        fail_scenario "B statutory+empty" "UI toggle failed"
        snapshot scenario-b
        return
    }
    verify_scheduled || {
        fail_scenario "B statutory+empty" "alarm not scheduled after toggle"
        snapshot scenario-b
        return
    }
    adb logcat -c
    if wait_fired 400; then
        pass_scenario "B statutory type with empty dataset fired"
    else
        fail_scenario "B statutory+empty dataset" "did not fire; an empty dataset must never suppress alarms"
        annotate_error "B no-fire; app logcat: $(logcat_app_tail)"
        crash_report
    fi
    snapshot scenario-b
}

scenario_c() {
    note "=== C: third-party data URL -> data loads + statutory alarm fires ==="
    local future_start future_end
    future_start="$(date -u -d '+30 days' +%F)"
    future_end="$(date -u -d '+32 days' +%F)"
    cat >"$FIXTURES/holiday_c.json" <<JSON
[{"name": "FixtureFutureHoliday", "startDate": "$future_start", "endDate": "$future_end", "compDays": []}]
JSON
    local target h m
    target=$(( $(dev_epoch) + 240 ))
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    prepare_alarm_db "$h" "$m" 127 1 || {
        fail_scenario "C third-party source" "database setup failed"
        snapshot scenario-c
        return
    }
    # pm clear (inside prepare_alarm_db) wipes preferences, so the custom data URL is
    # written after the database setup, while the app is stopped.
    set_holiday_url_pref "http://10.0.2.2:8080/holiday_c.json"
    if ! update_holiday_data_via_ui; then
        fail_scenario "C third-party source" "data update through the UI failed"
        snapshot scenario-c
        return
    fi
    adb shell input keyevent BACK >/dev/null 2>&1
    sleep 2
    toggle_alarm_on_via_ui || {
        fail_scenario "C third-party source" "UI toggle failed"
        snapshot scenario-c
        return
    }
    verify_scheduled || {
        fail_scenario "C third-party source" "alarm not scheduled after data update + toggle"
        snapshot scenario-c
        return
    }
    adb logcat -c
    if wait_fired 400; then
        pass_scenario "C third-party data source loaded and alarm fired"
    else
        fail_scenario "C third-party source" "data loaded but alarm did not fire"
        annotate_error "C no-fire; app logcat: $(logcat_app_tail)"
        crash_report
    fi
    snapshot scenario-c
}

scenario_d() {
    note "=== D: statutory type + today is a legal holiday -> must NOT fire ==="
    local target tdate h m
    target=$(( $(dev_epoch) + 240 ))
    tdate="$(dev_date_field "$target" F)"
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    local extra="DELETE FROM holiday; INSERT INTO holiday (name, start_date, end_date, comp_days) VALUES ('FixtureHolidayD', '$tdate', '$tdate', '[]');"
    prepare_alarm_db "$h" "$m" 127 1 "$extra" || {
        fail_scenario "D legal holiday" "database setup failed"
        snapshot scenario-d
        return
    }
    toggle_alarm_on_via_ui || {
        fail_scenario "D legal holiday" "UI toggle failed"
        snapshot scenario-d
        return
    }
    verify_scheduled || {
        fail_scenario "D legal holiday" "alarm not scheduled after toggle"
        snapshot scenario-d
        return
    }
    adb logcat -c
    if wait_fired 400; then
        fail_scenario "D legal holiday" "alarm FIRED although today is a legal holiday"
    else
        pass_scenario "D legal holiday skipped (no ring)"
    fi
    snapshot scenario-d
}

scenario_e() {
    note "=== E: make-up workday today, weekly pattern excludes today -> must fire ==="
    local target tdate iso_dow cal_dow hol_start hol_end h m bits
    target=$(( $(dev_epoch) + 240 ))
    tdate="$(dev_date_field "$target" F)"
    h="$(dev_date_field "$target" H)"
    m="$(dev_date_field "$target" M)"
    iso_dow="$(dev_date_field "$target" u)"
    # Calendar day-of-week int: ISO 1..7 (Mon..Sun) -> Calendar 2..7,1
    cal_dow=$(( iso_dow == 7 ? 1 : iso_dow + 1 ))
    # Weekly pattern covers every day EXCEPT today's weekday: only the make-up day
    # logic can make the alarm ring today.
    bits=$(( 127 - $(cal_to_bit "$cal_dow") ))
    # A holiday next week whose make-up workday is today.
    hol_start="$(dev_date_field $(( target + 5 * 86400 )) F)"
    hol_end="$(dev_date_field $(( target + 6 * 86400 )) F)"
    local extra="DELETE FROM holiday; INSERT INTO holiday (name, start_date, end_date, comp_days) VALUES ('FixtureHolidayE', '$hol_start', '$hol_end', '[\"$tdate\"]');"
    prepare_alarm_db "$h" "$m" "$bits" 1 "$extra" || {
        fail_scenario "E make-up workday" "database setup failed"
        snapshot scenario-e
        return
    }
    toggle_alarm_on_via_ui || {
        fail_scenario "E make-up workday" "UI toggle failed"
        snapshot scenario-e
        return
    }
    verify_scheduled || {
        fail_scenario "E make-up workday" "alarm not scheduled after toggle"
        snapshot scenario-e
        return
    }
    adb logcat -c
    if wait_fired 400; then
        pass_scenario "E make-up workday fired"
    else
        fail_scenario "E make-up workday" "did not fire; the weekly pattern skipped the make-up day (PR #2 regression)"
        annotate_error "E no-fire; app logcat: $(logcat_app_tail)"
        crash_report
    fi
    snapshot scenario-e
}

main() {
    note "installing $APK"
    if ! adb install -r "$APK"; then
        echo "::error::adb install failed"
        exit 1
    fi
    if ! adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -q "$PKG"; then
        annotate_error "package $PKG missing after install"
        exit 1
    fi

    local sanity
    sanity="$(adb shell echo alarm-test-sanity 2>&1 | tr -d '\r\n')"
    if [ "$sanity" != "alarm-test-sanity" ]; then
        annotate_error "adb shell sanity check failed, got: '${sanity:0:200}'"
    else
        note "adb shell sanity: OK"
    fi

    wake
    launch_app
    sleep 5
    if [ -z "$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r\n')" ]; then
        annotate_error "app process not running after first launch; it may be crashing"
        crash_report
    else
        note "app launches and runs"
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
        echo "::error::${failed} alarm-test scenario(s) failed (evidence under scripts/alarm-test/logs)"
        exit 1
    fi
    note "all scenarios passed"
}

main "$@"
