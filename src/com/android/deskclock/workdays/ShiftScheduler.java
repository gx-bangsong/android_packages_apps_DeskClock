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
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Computes the next valid firing time for shift-rotation alarms.
 *
 * <p>The scheduler walks day by day from the next occurrence of the alarm time and checks every
 * candidate date against the {@link ShiftSchedule}: disabled cycle days are rest days, legal
 * holidays are skipped when holiday skipping is enabled, and make-up (compensation) workdays
 * always ring. The search window is bounded so that a schedule in which no valid day exists
 * (e.g. every cycle day disabled) yields {@code null} instead of looping forever.</p>
 */
public final class ShiftScheduler {

    /** Safety margin beyond the cycle length used to bound the day-by-day search. */
    private static final int SEARCH_SAFETY_DAYS = 400;

    private ShiftScheduler() {}

    /**
     * @param schedule the shift schedule to evaluate
     * @param alarmTime the wall-clock time of day at which the alarm rings
     * @param now the current date and time
     * @param holidays holiday information, may be null if no data is available
     * @return the next valid firing time, or {@code null} if no valid day exists
     */
    public static LocalDateTime nextAlarmTime(ShiftSchedule schedule, LocalTime alarmTime,
            LocalDateTime now, HolidayProvider holidays) {
        if (!schedule.hasAnyEnabledDay()) {
            // Every cycle day is a rest day: there is no valid firing time.
            return null;
        }

        LocalDateTime candidate = now.toLocalDate().atTime(alarmTime);
        if (!isAfter(candidate, now)) {
            candidate = candidate.plusDays(1);
        }

        final int maxIterations = schedule.getCycleDays() * 2 + SEARCH_SAFETY_DAYS;
        for (int i = 0; i < maxIterations; i++) {
            if (isWorkday(schedule, candidate.toLocalDate(), holidays)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return null;
    }

    /**
     * Compares two date-times without relying on {@code LocalDateTime} ordering methods, which
     * have interface-typed parameters that vary across java.time API levels.
     */
    private static boolean isAfter(LocalDateTime candidate, LocalDateTime reference) {
        final long candidateMinutes = candidate.toLocalDate().toEpochDay() * 24 * 60
                + candidate.toLocalTime().getHour() * 60L + candidate.toLocalTime().getMinute();
        final long referenceMinutes = reference.toLocalDate().toEpochDay() * 24 * 60
                + reference.toLocalTime().getHour() * 60L + reference.toLocalTime().getMinute();
        return candidateMinutes > referenceMinutes;
    }

    /**
     * @param schedule the shift schedule to evaluate
     * @param date the date to check
     * @param holidays holiday information, may be null if no data is available
     * @return {@code true} if the alarm should ring on {@code date}
     */
    public static boolean isWorkday(ShiftSchedule schedule, LocalDate date,
            HolidayProvider holidays) {
        if (!schedule.isDayEnabled(schedule.dayIndexFor(date))) {
            // Disabled cycle days are rest days.
            return false;
        }
        if (schedule.isSkipHolidays() && holidays != null) {
            // Make-up workdays always ring, even when holiday skipping is enabled.
            if (holidays.isCompWorkday(date)) {
                return true;
            }
            if (holidays.isLegalHoliday(date)) {
                return false;
            }
        }
        return true;
    }
}
