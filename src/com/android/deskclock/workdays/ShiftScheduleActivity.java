/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.deskclock.workdays;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.android.deskclock.AsyncHandler;
import com.android.deskclock.EdgeToEdgeUtils;
import com.android.deskclock.R;
import com.android.deskclock.provider.Alarm;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Locale;

/**
 * Editor for the shift-rotation schedule of an alarm.
 *
 * <p>The editor maintains a working copy of the {@link ShiftSchedule}; "Done" persists it to the
 * alarm and re-schedules the next instance, "Cancel" discards the changes. The per-day list is
 * anchored to the shift cycle that contains today, so the "today" marker is always visible and
 * the current period is recomputed whenever the cycle length or the start date changes.</p>
 */
public final class ShiftScheduleActivity extends FragmentActivity {

    /** Extra key for the id of the alarm being edited. */
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";

    private static final String PERIOD_DATE_PATTERN = "yyyy.MM.dd";

    private long mAlarmId = Alarm.INVALID_ID;
    private Alarm mAlarm;

    /** The working copy of the shift schedule being edited. */
    private ShiftSchedule mSchedule;

    private TextView mCycleValue;
    private TextView mStartDateValue;
    private TextView mPeriodValue;
    private MaterialSwitch mSkipHolidaySwitch;
    private LinearLayout mDaysContainer;
    private TextView mAllOffWarning;

    /**
     * @param context a context
     * @param alarmId the id of the alarm whose shift schedule is edited
     * @return an intent that opens the shift schedule editor
     */
    public static Intent createIntent(Context context, long alarmId) {
        return new Intent(context, ShiftScheduleActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarmId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtils.configureWindow(getWindow());
        setContentView(R.layout.activity_shift_schedule);
        EdgeToEdgeUtils.applyInsets(findViewById(android.R.id.content));

        mAlarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, Alarm.INVALID_ID);
        mAlarm = mAlarmId == Alarm.INVALID_ID ? null : Alarm.getAlarm(getContentResolver(), mAlarmId);
        if (mAlarm == null) {
            finish();
            return;
        }

        mSchedule = mAlarm.createShiftSchedule();

        mCycleValue = findViewById(R.id.shift_cycle_value);
        mStartDateValue = findViewById(R.id.shift_start_date_value);
        mPeriodValue = findViewById(R.id.shift_current_period_value);
        mSkipHolidaySwitch = findViewById(R.id.shift_skip_holiday_switch);
        mDaysContainer = findViewById(R.id.shift_days_container);
        mAllOffWarning = findViewById(R.id.shift_all_off_warning);

        findViewById(R.id.shift_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.shift_done).setOnClickListener(v -> saveSchedule());
        findViewById(R.id.shift_cycle_row).setOnClickListener(v -> showCyclePickerDialog());
        findViewById(R.id.shift_start_date_row).setOnClickListener(v -> showStartDatePickerDialog());

        mSkipHolidaySwitch.setOnCheckedChangeListener((button, checked) -> {
            mSchedule = mSchedule.withSkipHolidays(checked);
            rebuild();
        });

        rebuild();
    }

    /** Rebuilds all dynamic content from the current working schedule. */
    private void rebuild() {
        mCycleValue.setText(getString(R.string.shift_cycle_days_value, mSchedule.getCycleDays()));
        mStartDateValue.setText(formatStartDate(mSchedule.getStartDate()));

        final LocalDate today = LocalDate.now();
        final LocalDate cycleStart = mSchedule.cycleStartFor(today);
        final LocalDate cycleEnd = mSchedule.cycleEndFor(today);
        mPeriodValue.setText(getString(R.string.shift_current_period_value,
                formatPeriodDate(cycleStart), formatPeriodDate(cycleEnd)));

        mSkipHolidaySwitch.setOnCheckedChangeListener(null);
        mSkipHolidaySwitch.setChecked(mSchedule.isSkipHolidays());
        mSkipHolidaySwitch.setOnCheckedChangeListener((button, checked) -> {
            mSchedule = mSchedule.withSkipHolidays(checked);
            rebuild();
        });

        rebuildDayRows(today, cycleStart);

        mAllOffWarning.setVisibility(mSchedule.hasAnyEnabledDay() ? View.GONE : View.VISIBLE);
    }

    private void rebuildDayRows(LocalDate today, LocalDate cycleStart) {
        mDaysContainer.removeAllViews();
        final LayoutInflater inflater = getLayoutInflater();
        final LocalTime fallbackTime = LocalTime.of(mAlarm.hour, mAlarm.minutes);
        for (int i = 0; i < mSchedule.getCycleDays(); i++) {
            final int dayIndex = i;
            final LocalDate date = cycleStart.plusDays(dayIndex);
            final boolean enabled = mSchedule.isDayEnabled(dayIndex);
            final LocalTime dayTime = mSchedule.getDayTime(dayIndex, fallbackTime);

            final View row = inflater.inflate(R.layout.shift_day_item, mDaysContainer, false);
            ((TextView) row.findViewById(R.id.shift_day_title))
                    .setText(getString(R.string.shift_day_title, dayIndex + 1));
            final TextView dayTimeView = row.findViewById(R.id.shift_day_time);
            dayTimeView.setText(DateFormat.getTimeFormat(this).format(
                    timeCalendar(dayTime).getTime()));
            row.setOnClickListener(v -> showTimePickerDialog(dayIndex));
            dayTimeView.setOnClickListener(v -> showTimePickerDialog(dayIndex));

            final TextView todayView = row.findViewById(R.id.shift_day_today);
            todayView.setVisibility(date.equals(today) ? View.VISIBLE : View.GONE);

            final TextView restView = row.findViewById(R.id.shift_day_rest);
            restView.setVisibility(enabled ? View.GONE : View.VISIBLE);

            final MaterialSwitch daySwitch = row.findViewById(R.id.shift_day_switch);
            daySwitch.setChecked(enabled);
            daySwitch.setOnCheckedChangeListener((button, checked) -> {
                mSchedule = mSchedule.withDayEnabled(dayIndex, checked);
                restView.setVisibility(checked ? View.GONE : View.VISIBLE);
                mAllOffWarning.setVisibility(
                        mSchedule.hasAnyEnabledDay() ? View.GONE : View.VISIBLE);
            });
            mDaysContainer.addView(row);
        }
    }

    /** Shows a time picker for the selected cycle day. */
    private void showTimePickerDialog(int dayIndex) {
        final LocalTime fallbackTime = LocalTime.of(mAlarm.hour, mAlarm.minutes);
        final LocalTime currentTime = mSchedule.getDayTime(dayIndex, fallbackTime);
        final boolean is24Hour = DateFormat.is24HourFormat(this);
        new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    mSchedule = mSchedule.withDayTime(dayIndex, LocalTime.of(hourOfDay, minute));
                    rebuild();
                },
                currentTime.getHour(), currentTime.getMinute(), is24Hour)
                .show();
    }

    /** Shows a NumberPicker dialog for the cycle length. */
    private void showCyclePickerDialog() {
        final NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(ShiftSchedule.MIN_CYCLE_DAYS);
        picker.setMaxValue(ShiftSchedule.MAX_CYCLE_DAYS);
        picker.setValue(mSchedule.getCycleDays());
        picker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(this)
                .setTitle(R.string.shift_cycle_title)
                .setView(picker)
                .setPositiveButton(R.string.done, (dialog, which) -> {
                    final int days = picker.getValue();
                    if (days >= ShiftSchedule.MIN_CYCLE_DAYS
                            && days <= ShiftSchedule.MAX_CYCLE_DAYS) {
                        mSchedule = mSchedule.withCycleDays(days);
                        rebuild();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Shows a system date picker for the start date. */
    private void showStartDatePickerDialog() {
        final LocalDate startDate = mSchedule.getStartDate();
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    mSchedule = mSchedule.withStartDate(LocalDate.of(year, month + 1, dayOfMonth));
                    rebuild();
                },
                startDate.getYear(), startDate.getMonthValue() - 1, startDate.getDayOfMonth())
                .show();
    }

    /** Persists the schedule and re-schedules the alarm on a background thread. */
    private void saveSchedule() {
        final Context appContext = getApplicationContext();
        final Alarm alarm = mAlarm;
        alarm.workdayType = WorkdayType.SHIFT;
        alarm.shiftCycleDays = mSchedule.getCycleDays();
        alarm.shiftStartDate = mSchedule.getStartDate().toString();
        alarm.shiftSkipHoliday = mSchedule.isSkipHolidays();
        alarm.shiftDaysMask = mSchedule.getDaysMask();
        alarm.shiftTimes = mSchedule.getTimesMask();

        AsyncHandler.post(() -> {
            try {
                Alarm.updateAlarm(appContext.getContentResolver(), alarm);
                WorkdayAlarmScheduler.rescheduleAlarm(appContext, alarm);
            } finally {
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }

    /** @return a calendar at the given alarm time on today's date */
    private Calendar timeCalendar(LocalTime time) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, time.getHour());
        calendar.set(Calendar.MINUTE, time.getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    /** Formats the start date using the device locale's medium date format. */
    private String formatStartDate(LocalDate date) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return DateFormat.getMediumDateFormat(this).format(calendar.getTime());
    }

    /** Formats a period boundary as {@code yyyy.MM.dd}. */
    private static String formatPeriodDate(LocalDate date) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return new SimpleDateFormat(PERIOD_DATE_PATTERN, Locale.US).format(calendar.getTime());
    }
}
