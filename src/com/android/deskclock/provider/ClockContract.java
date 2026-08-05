/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.deskclock.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import com.android.deskclock.BuildConfig;

/**
 * <p>
 * The contract between the clock provider and desk clock. Contains
 * definitions for the supported URIs and data columns.
 * </p>
 * <h3>Overview</h3>
 * <p>
 * ClockContract defines the data model of clock related information.
 * This data is stored in a number of tables:
 * </p>
 * <ul>
 * <li>The {@link AlarmsColumns} table holds the user created alarms</li>
 * <li>The {@link InstancesColumns} table holds the current state of each
 * alarm in the AlarmsColumn table.
 * </li>
 * </ul>
 */
public final class ClockContract {
    /**
     * This authority is used for writing to or querying from the clock
     * provider.
     */
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID;

    /**
     * This utility class cannot be instantiated
     */
    private ClockContract() {}

    /**
     * Constants for tables with AlarmSettings.
     */
    private interface AlarmSettingColumns extends BaseColumns {
        /**
         * This string is used to indicate no ringtone.
         */
        Uri NO_RINGTONE_URI = Uri.EMPTY;

        /**
         * True if alarm should vibrate
         * <p>Type: BOOLEAN</p>
         */
        String VIBRATE = "vibrate";

        /**
         * Alarm label.
         *
         * <p>Type: STRING</p>
         */
        String LABEL = "label";

        /**
         * Audio alert to play when alarm triggers. Null entry
         * means use system default and entry that equal
         * Uri.EMPTY.toString() means no ringtone.
         *
         * <p>Type: STRING</p>
         */
        String RINGTONE = "ringtone";

        /**
         * True if alarm should start off quiet and slowly increase volume
         * <P>Type: BOOLEAN</P>
         */
        String INCREASING_VOLUME = "incvol";
    }

    /**
     * Constants for the Alarms table, which contains the user created alarms.
     */
    protected interface AlarmsColumns extends AlarmSettingColumns, BaseColumns {
        /**
         * The content:// style URL for this table.
         */
        Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/alarms");

        /**
         * The content:// style URL for the alarms with instance tables, which is used to get the
         * next firing instance and the current state of an alarm.
         */
        Uri ALARMS_WITH_INSTANCES_URI = Uri.parse("content://" + AUTHORITY
                + "/alarms_with_instances");

        /**
         * Hour in 24-hour localtime 0 - 23.
         * <p>Type: INTEGER</p>
         */
        String HOUR = "hour";

        /**
         * Minutes in localtime 0 - 59.
         * <p>Type: INTEGER</p>
         */
        String MINUTES = "minutes";

        /**
         * Days of the week encoded as a bit set.
         * <p>Type: INTEGER</p>
         *
         * {@link com.android.deskclock.data.Weekdays}
         */
        String DAYS_OF_WEEK = "daysofweek";

        /**
         * True if alarm is active.
         * <p>Type: BOOLEAN</p>
         */
        String ENABLED = "enabled";

        /**
         * Determine if alarm is deleted after it has been used.
         * <p>Type: INTEGER</p>
         */
        String DELETE_AFTER_USE = "delete_after_use";

        /**
         * The workday type assigned to this alarm; one of the constants of
         * {@link com.android.deskclock.workdays.WorkdayType}. Zero (the default) means the
         * alarm follows the plain weekly/one-time schedule.
         * <p>Type: INTEGER</p>
         */
        String WORKDAY_TYPE = "workday_type";

        /**
         * The number of days in one shift-rotation cycle; used only when
         * {@link #WORKDAY_TYPE} is {@link com.android.deskclock.workdays.WorkdayType#SHIFT}.
         * <p>Type: INTEGER</p>
         */
        String SHIFT_CYCLE_DAYS = "shift_cycle_days";

        /**
         * The date of day 1 of the shift-rotation cycle, stored as a local date in
         * {@code yyyy-MM-dd} form; used only when {@link #WORKDAY_TYPE} is
         * {@link com.android.deskclock.workdays.WorkdayType#SHIFT}.
         * <p>Type: TEXT</p>
         */
        String SHIFT_START_DATE = "shift_start_date";

        /**
         * Whether legal holidays are skipped for this shift alarm; make-up workdays still ring.
         * <p>Type: BOOLEAN</p>
         */
        String SHIFT_SKIP_HOLIDAY = "shift_skip_holiday";

        /**
         * The per-day enabled state of the shift cycle encoded as a string of '1' and '0'
         * characters (index 0 = day 1 of the cycle); used only when {@link #WORKDAY_TYPE} is
         * {@link com.android.deskclock.workdays.WorkdayType#SHIFT}.
         * <p>Type: TEXT</p>
         */
        String SHIFT_DAYS_MASK = "shift_days_mask";
    }

    /**
     * Constants for the Holiday table, which stores the legal holidays and make-up workdays
     * downloaded from a user-configurable data source. Public because the holiday data is
     * accessed from {@code com.android.deskclock.holiday}.
     */
    public interface HolidayColumns extends BaseColumns {
        /**
         * The display name of the holiday.
         * <p>Type: TEXT</p>
         */
        String NAME = "name";

        /**
         * The first day of the holiday, stored as {@code yyyy-MM-dd}.
         * <p>Type: TEXT</p>
         */
        String START_DATE = "start_date";

        /**
         * The last day of the holiday, stored as {@code yyyy-MM-dd}.
         * <p>Type: TEXT</p>
         */
        String END_DATE = "end_date";

        /**
         * The make-up workdays of the holiday, stored as a JSON array of {@code yyyy-MM-dd}
         * strings.
         * <p>Type: TEXT</p>
         */
        String COMP_DAYS = "comp_days";
    }
    protected interface InstancesColumns extends AlarmSettingColumns, BaseColumns {
        /**
         * The content:// style URL for this table.
         */
        Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/instances");

        /**
         * Alarm state when to show no notification.
         *
         * Can transitions to:
         * LOW_NOTIFICATION_STATE
         */
        int SILENT_STATE = 0;

        /**
         * Alarm state to show low priority alarm notification.
         *
         * Can transitions to:
         * HIDE_NOTIFICATION_STATE
         * HIGH_NOTIFICATION_STATE
         * DISMISSED_STATE
         */
        int LOW_NOTIFICATION_STATE = 1;

        /**
         * Alarm state to hide low priority alarm notification.
         *
         * Can transitions to:
         * HIGH_NOTIFICATION_STATE
         */
        int HIDE_NOTIFICATION_STATE = 2;

        /**
         * Alarm state to show high priority alarm notification.
         *
         * Can transitions to:
         * DISMISSED_STATE
         * FIRED_STATE
         */
        int HIGH_NOTIFICATION_STATE = 3;

        /**
         * Alarm state when alarm is in snooze.
         *
         * Can transitions to:
         * DISMISSED_STATE
         * FIRED_STATE
         */
        int SNOOZE_STATE = 4;

        /**
         * Alarm state when alarm is being fired.
         *
         * Can transitions to:
         * DISMISSED_STATE
         * SNOOZED_STATE
         * MISSED_STATE
         */
        int FIRED_STATE = 5;

        /**
         * Alarm state when alarm has been missed.
         *
         * Can transitions to:
         * DISMISSED_STATE
         */
        int MISSED_STATE = 6;

        /**
         * Alarm state when alarm is done.
         */
        int DISMISSED_STATE = 7;

        /**
         * Alarm state when alarm has been dismissed before its intended firing time.
         */
        int PREDISMISSED_STATE = 8;

        /**
         * Alarm year.
         *
         * <p>Type: INTEGER</p>
         */
        String YEAR = "year";

        /**
         * Alarm month in year.
         *
         * <p>Type: INTEGER</p>
         */
        String MONTH = "month";

        /**
         * Alarm day in month.
         *
         * <p>Type: INTEGER</p>
         */
        String DAY = "day";

        /**
         * Alarm hour in 24-hour localtime 0 - 23.
         * <p>Type: INTEGER</p>
         */
        String HOUR = "hour";

        /**
         * Alarm minutes in localtime 0 - 59
         * <p>Type: INTEGER</p>
         */
        String MINUTES = "minutes";

        /**
         * Foreign key to Alarms table
         * <p>Type: INTEGER (long)</p>
         */
        String ALARM_ID = "alarm_id";

        /**
         * Alarm state
         * <p>Type: INTEGER</p>
         */
        String ALARM_STATE = "alarm_state";
    }
}
