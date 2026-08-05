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

import com.android.deskclock.holiday.HolidayProvider;

import java.time.LocalDate;
import java.util.Calendar;

/**
 * Decides whether an alarm configured with one of the non-shift workday types
 * ({@link WorkdayType#STATUTORY_WORKDAY}, {@link WorkdayType#SINGLE_DAY_OFF},
 * {@link WorkdayType#BIG_SMALL_SATURDAY_ON} or {@link WorkdayType#BIG_SMALL_SATURDAY_OFF})
 * should ring on a given date.
 *
 * <p>The day-of-week bit set follows the encoding used by {@code com.android.deskclock.data.Weekdays}:
 * Monday = 0x01, Tuesday = 0x02, Wednesday = 0x04, Thursday = 0x08, Friday = 0x10,
 * Saturday = 0x20, Sunday = 0x40.</p>
 *
 * <p>Big/small weeks are computed from continuous epoch weeks (weeks since 1970-01-01). Week
 * numbers are continuous across month and year boundaries, so the alternation can never produce
 * two consecutive identical weeks. The two {@code BIG_SMALL_*} variants are exact opposites and
 * only differ in which parity of week counts as the "big" week that works on Saturday.</p>
 */
public final class WorkdayPolicy {

    // Bit positions matching com.android.deskclock.data.Weekdays for Calendar.DAY_OF_WEEK values.
    private static final int MONDAY_BIT = 0x01;    // Calendar.MONDAY = 2
    private static final int TUESDAY_BIT = 0x02;   // Calendar.TUESDAY = 3
    private static final int WEDNESDAY_BIT = 0x04; // Calendar.WEDNESDAY = 4
    private static final int THURSDAY_BIT = 0x08;  // Calendar.THURSDAY = 5
    private static final int FRIDAY_BIT = 0x10;    // Calendar.FRIDAY = 6
    private static final int SATURDAY_BIT = 0x20;  // Calendar.SATURDAY = 7
    private static final int SUNDAY_BIT = 0x40;    // Calendar.SUNDAY = 1

    private WorkdayPolicy() {}

    /**
     * @param workdayType one of the {@link WorkdayType} constants
     * @param date the date to check
     * @param daysOfWeekBits the alarm's weekly repeat pattern bitset (Weekdays encoding)
     * @param holidays holiday information, may be null if no data is available
     * @return {@code true} if the alarm should ring on {@code date}
     */
    public static boolean shouldRing(int workdayType, LocalDate date, int daysOfWeekBits,
            HolidayProvider holidays) {
        if (workdayType == WorkdayType.NONE) {
            return true;
        }

        final int calendarDayOfWeek = toCalendarDayOfWeek(date);
        final boolean isCompWorkday = holidays != null && holidays.isCompWorkday(date);
        final boolean isLegalHoliday = holidays != null && holidays.isLegalHoliday(date);

        switch (workdayType) {
            case WorkdayType.STATUTORY_WORKDAY:
                // 法定工作日: make-up workdays ring, legal holidays are silent, otherwise the
                // alarm follows its own weekly repeat pattern.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                return isBitOn(daysOfWeekBits, calendarDayOfWeek);

            case WorkdayType.SINGLE_DAY_OFF:
                // 单休制（仅休周日）: work Monday through Saturday, rest Sunday.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                return calendarDayOfWeek != Calendar.SUNDAY;

            case WorkdayType.BIG_SMALL_SATURDAY_ON:
                // 大小周制（本周休周日）: in big weeks Saturday is a workday (only Sunday off).
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                if (calendarDayOfWeek == Calendar.SUNDAY) return false;
                if (calendarDayOfWeek == Calendar.SATURDAY) return isBigWeek(date);
                return true;

            case WorkdayType.BIG_SMALL_SATURDAY_OFF:
                // 大小周制（本周休周六、日）: in small weeks Saturday is a rest day.
                if (isCompWorkday) return true;
                if (isLegalHoliday) return false;
                if (calendarDayOfWeek == Calendar.SUNDAY) return false;
                if (calendarDayOfWeek == Calendar.SATURDAY) return !isBigWeek(date);
                return true;

            default:
                return true;
        }
    }

    /**
     * @param date a date
     * @return {@code true} if the week containing {@code date} is a "big" week, i.e. a week in
     *         which Saturday is a workday under the big/small week alternation
     */
    public static boolean isBigWeek(LocalDate date) {
        final long epochWeek = Math.floorDiv(date.toEpochDay(), 7L);
        return epochWeek % 2 == 0;
    }

    /**
     * Converts the ISO day-of-week of a date to the {@link Calendar#DAY_OF_WEEK} encoding
     * (Sunday = 1, Monday = 2, ... Saturday = 7).
     */
    private static int toCalendarDayOfWeek(LocalDate date) {
        final int isoDay = date.getDayOfWeek().getValue(); // Monday = 1 ... Sunday = 7
        return isoDay == 7 ? Calendar.SUNDAY : isoDay + 1;
    }

    private static boolean isBitOn(int bits, int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case Calendar.MONDAY: return (bits & MONDAY_BIT) != 0;
            case Calendar.TUESDAY: return (bits & TUESDAY_BIT) != 0;
            case Calendar.WEDNESDAY: return (bits & WEDNESDAY_BIT) != 0;
            case Calendar.THURSDAY: return (bits & THURSDAY_BIT) != 0;
            case Calendar.FRIDAY: return (bits & FRIDAY_BIT) != 0;
            case Calendar.SATURDAY: return (bits & SATURDAY_BIT) != 0;
            case Calendar.SUNDAY: return (bits & SUNDAY_BIT) != 0;
            default: return false;
        }
    }
}
