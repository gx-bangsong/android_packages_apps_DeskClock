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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;

import com.android.deskclock.AsyncHandler;
import com.android.deskclock.R;
import com.android.deskclock.provider.Alarm;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user pick the workday type of a single alarm. Choosing one of the first four options
 * saves the selection immediately. Choosing "shift rotation" saves the shift type and opens the
 * shift schedule editor ({@link ShiftScheduleActivity}).
 */
public final class WorkdayTypeActivity extends FragmentActivity {

    /** Extra key for the id of the alarm being edited. */
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";

    private static final class Option {
        final int mType;
        final @StringRes int mTitle;
        final @StringRes int mSummary;

        Option(int type, @StringRes int title, @StringRes int summary) {
            mType = type;
            mTitle = title;
            mSummary = summary;
        }
    }

    /** The options in display order. */
    private static final Option[] OPTIONS = {
            new Option(WorkdayType.STATUTORY_WORKDAY,
                    R.string.workday_type_statutory, R.string.workday_type_statutory_summary),
            new Option(WorkdayType.SINGLE_DAY_OFF,
                    R.string.workday_type_single_off, R.string.workday_type_single_off_summary),
            new Option(WorkdayType.BIG_SMALL_SATURDAY_ON,
                    R.string.workday_type_big_small_sun,
                    R.string.workday_type_big_small_sun_summary),
            new Option(WorkdayType.BIG_SMALL_SATURDAY_OFF,
                    R.string.workday_type_big_small_weekend,
                    R.string.workday_type_big_small_weekend_summary),
            new Option(WorkdayType.SHIFT,
                    R.string.workday_type_shift, R.string.workday_type_shift_summary),
    };

    private long mAlarmId = Alarm.INVALID_ID;
    private Alarm mAlarm;
    private final List<RadioButton> mRadios = new ArrayList<>(OPTIONS.length);

    /**
     * @param context a context
     * @param alarmId the id of the alarm whose workday type is edited
     * @return an intent that opens the workday type page
     */
    public static Intent createIntent(Context context, long alarmId) {
        return new Intent(context, WorkdayTypeActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarmId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workday_type);

        mAlarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, Alarm.INVALID_ID);

        final LinearLayout list = findViewById(R.id.workday_type_list);
        final LayoutInflater inflater = getLayoutInflater();
        for (final Option option : OPTIONS) {
            final View row = inflater.inflate(R.layout.workday_type_option, list, false);
            ((TextView) row.findViewById(R.id.workday_option_title)).setText(option.mTitle);
            ((TextView) row.findViewById(R.id.workday_option_summary)).setText(option.mSummary);
            final RadioButton radio = row.findViewById(R.id.workday_option_radio);
            mRadios.add(radio);
            row.setOnClickListener(v -> onOptionSelected(option.mType));
            list.addView(row);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload the alarm so the selection reflects the latest stored configuration (e.g. after
        // returning from the shift schedule editor).
        mAlarm = mAlarmId == Alarm.INVALID_ID ? null : Alarm.getAlarm(getContentResolver(), mAlarmId);
        refreshSelection();
    }

    private void refreshSelection() {
        final int selected = mAlarm == null ? WorkdayType.NONE : mAlarm.workdayType;
        for (int i = 0; i < OPTIONS.length; i++) {
            mRadios.get(i).setChecked(OPTIONS[i].mType == selected);
        }
    }

    private void onOptionSelected(int type) {
        if (mAlarm == null) {
            return;
        }
        if (type == WorkdayType.SHIFT) {
            if (mAlarm.workdayType != WorkdayType.SHIFT) {
                mAlarm.workdayType = WorkdayType.SHIFT;
                saveAlarm();
            }
            startActivity(ShiftScheduleActivity.createIntent(this, mAlarm.id));
            return;
        }
        if (mAlarm.workdayType == type) {
            // Re-selecting the current option simply closes the page.
            finish();
            return;
        }
        mAlarm.workdayType = type;
        saveAlarm();
        finish();
    }

    /** Persists the workday type and re-schedules the alarm on a background thread. */
    private void saveAlarm() {
        final Context appContext = getApplicationContext();
        final Alarm alarm = mAlarm;
        AsyncHandler.post(() -> {
            Alarm.updateAlarm(appContext.getContentResolver(), alarm);
            WorkdayAlarmScheduler.rescheduleAlarm(appContext, alarm);
        });
    }
}
