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

import com.android.deskclock.R;
import com.android.deskclock.provider.Alarm;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Small helpers for displaying workday types in the alarm list.
 */
public final class WorkdayTypeUtils {

    private static final String[] CHINESE_DIGITS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九"
    };

    private WorkdayTypeUtils() {}

    /**
     * @param context a context
     * @param workdayType one of the {@link WorkdayType} constants
     * @return the user-facing label of the workday type
     */
    public static String getLabel(Context context, int workdayType) {
        switch (workdayType) {
            case WorkdayType.STATUTORY_WORKDAY:
                return context.getString(R.string.workday_type_statutory);
            case WorkdayType.SINGLE_DAY_OFF:
                return context.getString(R.string.workday_type_single_off);
            case WorkdayType.BIG_SMALL_SATURDAY_ON:
                return context.getString(R.string.workday_type_big_small_sun);
            case WorkdayType.BIG_SMALL_SATURDAY_OFF:
                return context.getString(R.string.workday_type_big_small_weekend);
            case WorkdayType.SHIFT:
                return context.getString(R.string.workday_type_shift);
            default:
                return context.getString(R.string.workday_type_none);
        }
    }

    /**
     * Returns the current cycle position for a shift alarm, for example "轮班制第一天，共三天".
     * The position is based on today's date rather than the next firing date so it remains useful
     * even when today's cycle day is disabled.
     */
    public static String getShiftDescription(Context context, Alarm alarm) {
        final ShiftSchedule schedule = alarm.createShiftSchedule();
        final int day = schedule.dayIndexFor(LocalDate.now()) + 1;
        final int total = schedule.getCycleDays();
        if (isChinese(context)) {
            return context.getString(R.string.workday_type_shift_day,
                    toChineseNumber(day), toChineseNumber(total));
        }
        return context.getString(R.string.workday_type_shift_day,
                String.valueOf(day), String.valueOf(total));
    }

    private static boolean isChinese(Context context) {
        final String language = context.getResources().getConfiguration().getLocales()
                .get(0).getLanguage();
        return Locale.CHINESE.getLanguage().equals(language);
    }

    /** Formats the range used by shift cycles in natural Chinese numerals. */
    private static String toChineseNumber(int value) {
        if (value <= 0) {
            return CHINESE_DIGITS[0];
        }
        if (value < 10) {
            return CHINESE_DIGITS[value];
        }
        if (value < 20) {
            return "十" + (value == 10 ? "" : CHINESE_DIGITS[value - 10]);
        }
        if (value < 100) {
            final int tens = value / 10;
            final int ones = value % 10;
            return CHINESE_DIGITS[tens] + "十" + (ones == 0 ? "" : CHINESE_DIGITS[ones]);
        }
        final int hundreds = value / 100;
        final int remainder = value % 100;
        if (remainder == 0) {
            return CHINESE_DIGITS[hundreds] + "百";
        }
        if (remainder < 10) {
            return CHINESE_DIGITS[hundreds] + "百零" + CHINESE_DIGITS[remainder];
        }
        return CHINESE_DIGITS[hundreds] + "百" + toChineseNumber(remainder);
    }
}
