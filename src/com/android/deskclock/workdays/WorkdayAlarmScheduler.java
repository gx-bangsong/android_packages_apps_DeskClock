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

import android.content.ContentResolver;
import android.content.Context;

import com.android.deskclock.LogUtils;
import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;

import java.util.Calendar;
import java.util.List;

/**
 * Recomputes and re-schedules alarm instances that use workday logic. This is invoked whenever
 * the workday configuration of an alarm changes or the holiday dataset is updated, so the next
 * instance always reflects the newest configuration. Alarms without workday logic are never
 * touched.
 */
public final class WorkdayAlarmScheduler {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("WorkdayAlarmScheduler");

    private WorkdayAlarmScheduler() {}

    /**
     * Deletes all existing instances of the given alarm and creates a fresh one from the current
     * configuration. An alarm with no valid next firing time (e.g. every shift day disabled)
     * simply ends up without an instance.
     *
     * @param context application context
     * @param alarm the alarm to re-schedule
     */
    public static void rescheduleAlarm(Context context, Alarm alarm) {
        final ContentResolver cr = context.getContentResolver();
        AlarmStateManager.deleteAllInstances(context, alarm.id);
        if (!alarm.enabled) {
            return;
        }
        final AlarmInstance instance = alarm.createInstanceAfter(Calendar.getInstance());
        if (instance == null) {
            LOGGER.i("No valid next firing time for alarm %d; leaving it without an instance",
                    alarm.id);
            return;
        }
        AlarmInstance.addInstance(cr, instance);
        AlarmStateManager.registerInstance(context, instance, true);
    }

    /**
     * Re-schedules every alarm that uses workday logic (any {@link WorkdayType} other than
     * {@link WorkdayType#NONE}). Plain alarms are left untouched.
     *
     * @param context application context
     */
    public static void rescheduleAllWorkdayAlarms(Context context) {
        final List<Alarm> alarms = Alarm.getAlarms(context.getContentResolver(), null);
        for (Alarm alarm : alarms) {
            if (alarm.workdayType != WorkdayType.NONE) {
                rescheduleAlarm(context, alarm);
            }
        }
    }
}
