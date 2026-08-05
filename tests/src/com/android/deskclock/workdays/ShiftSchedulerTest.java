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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.deskclock.holiday.HolidayProvider;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Exercises the next-firing-time computation for shift alarms: basic scheduling, disabled days,
 * holidays, make-up workdays, month/year boundaries and the all-days-off case.
 */
public class ShiftSchedulerTest {

    /** Start date: Saturday 2026-04-25. */
    private static final LocalDate START = LocalDate.of(2026, 4, 25);

    /** A holiday on 2026-05-01 (Friday) and a make-up workday on 2026-05-09 (Saturday). */
    private static final HolidayProvider HOLIDAYS = new HolidayProvider() {
        @Override
        public boolean isLegalHoliday(LocalDate date) {
            return date.equals(LocalDate.of(2026, 5, 1));
        }

        @Override
        public boolean isCompWorkday(LocalDate date) {
            return date.equals(LocalDate.of(2026, 5, 9));
        }
    };

    /** A provider that reports the same holiday on both 2026-05-01 and 2026-05-02. */
    private static final HolidayProvider LONG_HOLIDAYS = new HolidayProvider() {
        @Override
        public boolean isLegalHoliday(LocalDate date) {
            return date.equals(LocalDate.of(2026, 5, 1))
                    || date.equals(LocalDate.of(2026, 5, 2));
        }

        @Override
        public boolean isCompWorkday(LocalDate date) {
            return false;
        }
    };

    private static ShiftSchedule schedule(int cycleDays, boolean skipHolidays,
            boolean... enabled) {
        final boolean[] days = new boolean[cycleDays];
        for (int i = 0; i < cycleDays && i < enabled.length; i++) {
            days[i] = enabled[i];
        }
        return new ShiftSchedule(cycleDays, START, skipHolidays, days);
    }

    @Test
    public void nextAlarmRingsAtTheAlarmTime() {
        final ShiftSchedule schedule = schedule(9, false,
                true, true, true, true, true, true, true, true, true);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 25, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 4, 25, 9, 39), next);
    }

    @Test
    public void nextAlarmMovesToTomorrowWhenTodayIsPast() {
        final ShiftSchedule schedule = schedule(9, false,
                true, true, true, true, true, true, true, true, true);
        // The alarm time already passed today; the next firing is tomorrow.
        final LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 4, 26, 9, 39), next);
    }

    @Test
    public void disabledDaysAreSkipped() {
        // Only day 4 (index 3, 2026-04-28) is enabled.
        final ShiftSchedule schedule = schedule(9, false,
                false, false, false, true, false, false, false, false, false);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 25, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 4, 28, 9, 39), next);
    }

    @Test
    public void crossesMonthBoundaries() {
        // Only day 1 (index 0) is enabled. The alarm time of 2026-04-25 (day 1) has already
        // passed, so the next firing is 2026-05-04 (the next day 1, across the month boundary).
        final ShiftSchedule schedule = schedule(9, false,
                true, false, false, false, false, false, false, false, false);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 5, 4, 9, 39), next);
    }

    @Test
    public void crossesYearBoundaries() {
        // Only the last day of a 9-day cycle (index 8) is enabled. The cycle containing
        // 2026-12-31 spans the year boundary (2026-12-24 .. 2027-01-01); index 8 is 2027-01-01.
        final ShiftSchedule schedule = schedule(9, false,
                false, false, false, false, false, false, false, false, true);
        final LocalDateTime now = LocalDateTime.of(2026, 12, 31, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 39), next);
    }

    @Test
    public void holidaysAreSkippedWhenEnabled() {
        // All days enabled, holidays skipped: 2026-05-01 and 2026-05-02 are holidays.
        final ShiftSchedule schedule = schedule(9, true,
                true, true, true, true, true, true, true, true, true);
        final LocalDateTime now = LocalDateTime.of(2026, 5, 1, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, LONG_HOLIDAYS);
        assertEquals(LocalDateTime.of(2026, 5, 3, 9, 39), next);
    }

    @Test
    public void holidaysAreNotSkippedWhenDisabled() {
        final ShiftSchedule schedule = schedule(9, false,
                true, true, true, true, true, true, true, true, true);
        final LocalDateTime now = LocalDateTime.of(2026, 5, 1, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 5, 1, 9, 39), next);
    }

    @Test
    public void makeupWorkdaysStillRing() {
        // 2026-05-09 (Saturday) is a make-up workday.
        final ShiftSchedule schedule = schedule(9, true,
                true, true, true, true, true, true, true, true, true);
        final LocalDateTime now = LocalDateTime.of(2026, 5, 9, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, HolidayProvider.EMPTY);
        assertEquals(LocalDateTime.of(2026, 5, 9, 9, 39), next);
    }

    @Test
    public void allDaysDisabledYieldsNoFiringTime() {
        final ShiftSchedule schedule = schedule(9, true,
                false, false, false, false, false, false, false, false, false);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 25, 8, 0);
        assertNull(ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39), now,
                HolidayProvider.EMPTY));
    }

    @Test
    public void workdayCheckTreatsHolidaysAndCompDaysCorrectly() {
        final ShiftSchedule schedule = schedule(9, true,
                true, true, true, true, true, true, true, true, true);
        // A holiday is not a workday...
        assertFalse(ShiftScheduler.isWorkday(schedule, LocalDate.of(2026, 5, 1), HOLIDAYS));
        // ...a make-up workday is...
        assertTrue(ShiftScheduler.isWorkday(schedule, LocalDate.of(2026, 5, 9), HOLIDAYS));
        // ...and a normal day is.
        assertTrue(ShiftScheduler.isWorkday(schedule, LocalDate.of(2026, 5, 2), HOLIDAYS));
        // Disabled days are rest days even when holiday skipping is off.
        final ShiftSchedule withRest = schedule(9, false,
                true, false, true, true, true, true, true, true, true);
        assertFalse(ShiftScheduler.isWorkday(withRest, LocalDate.of(2026, 4, 26), HOLIDAYS));
    }

    @Test
    public void nullHolidayProviderNeverCrashes() {
        final ShiftSchedule schedule = schedule(9, true,
                true, true, true, true, true, true, true, true, true);
        final LocalDateTime now = LocalDateTime.of(2026, 5, 1, 8, 0);
        final LocalDateTime next = ShiftScheduler.nextAlarmTime(schedule, LocalTime.of(9, 39),
                now, null);
        // Without holiday data nothing is skipped.
        assertEquals(LocalDateTime.of(2026, 5, 1, 9, 39), next);
    }
}
