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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.deskclock.holiday.HolidayProvider;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Exercises the non-shift workday types: statutory workdays, single day off and the two
 * big/small week variants, including holiday skipping and make-up workdays.
 *
 * <p>All assertions use fixed dates. Saturday 2026-04-25 is a make-up workday in the test
 * dataset; Sunday 2026-04-26 starts a two-day legal holiday.</p>
 */
public class WorkdayPolicyTest {

    private static final int MON_FRI_BITS = 0x01 | 0x02 | 0x04 | 0x08 | 0x10; // 31

    private static final HolidayProvider HOLIDAYS = new HolidayProvider() {
        @Override
        public boolean isLegalHoliday(LocalDate date) {
            return date.toEpochDay() >= LocalDate.of(2026, 4, 26).toEpochDay()
                    && date.toEpochDay() <= LocalDate.of(2026, 4, 27).toEpochDay();
        }

        @Override
        public boolean isCompWorkday(LocalDate date) {
            return date.equals(LocalDate.of(2026, 4, 25));
        }
    };

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    public void noWorkdayTypeAlwaysRings() {
        // Even on a holiday the plain schedule is not affected.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.NONE, date(2026, 4, 27), MON_FRI_BITS,
                HOLIDAYS));
    }

    @Test
    public void statutoryRingsOnSelectedWeekdays() {
        // Monday 2026-04-20 is a normal workday and selected in the weekly pattern.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 20), MON_FRI_BITS, HOLIDAYS));
        // Saturday is not part of the weekly pattern.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 18), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void statutorySkipsLegalHolidays() {
        // Sunday/Monday 2026-04-26/27 are a legal holiday; Monday is in the weekly pattern but
        // must not ring.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 27), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void statutoryRingsOnMakeupWorkdays() {
        // Saturday 2026-04-25 is a make-up workday; it must ring even though it is not part of
        // the weekly pattern.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 25), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void singleDayOffRingsMondayToSaturday() {
        // Friday 2026-04-24.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.SINGLE_DAY_OFF,
                date(2026, 4, 24), MON_FRI_BITS, HOLIDAYS));
        // Saturday 2026-04-18 is a workday under the single day off schedule.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.SINGLE_DAY_OFF,
                date(2026, 4, 18), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void singleDayOffRestsSunday() {
        // Sunday 2026-04-19.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.SINGLE_DAY_OFF,
                date(2026, 4, 19), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void singleDayOffSkipsHolidayAndRingsOnMakeupDay() {
        // Sunday 2026-04-26 is a holiday.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.SINGLE_DAY_OFF,
                date(2026, 4, 26), MON_FRI_BITS, HOLIDAYS));
        // Saturday 2026-04-25 is a make-up workday.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.SINGLE_DAY_OFF,
                date(2026, 4, 25), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void bigSmallSaturdayOnRingsOnBigWeekSaturdays() {
        // 2026-05-09 is a Saturday in an even epoch week (a "big" week).
        assertTrue(WorkdayPolicy.isBigWeek(date(2026, 5, 9)));
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 5, 9), MON_FRI_BITS, HOLIDAYS));
        // Saturday 2026-04-25 is also a big-week Saturday and a make-up workday; it must ring.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 4, 25), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void bigSmallSaturdayOnRestsOnSmallWeekSaturdays() {
        // 2026-04-18 is a Saturday in an odd ("small") epoch week: no ring.
        assertFalse(WorkdayPolicy.isBigWeek(date(2026, 4, 18)));
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 4, 18), MON_FRI_BITS, HOLIDAYS));
        // 2026-05-02 is another small-week Saturday.
        assertFalse(WorkdayPolicy.isBigWeek(date(2026, 5, 2)));
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 5, 2), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void bigSmallSaturdayOnAlwaysRestsSunday() {
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 4, 19), MON_FRI_BITS, HOLIDAYS));
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_ON,
                date(2026, 4, 26), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void bigSmallSaturdayOffIsTheOppositeVariant() {
        // 2026-04-18 is a small-week Saturday: works under BIG_SMALL_SATURDAY_OFF.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_OFF,
                date(2026, 4, 18), MON_FRI_BITS, HOLIDAYS));
        // 2026-05-09 is a big-week Saturday: rests under BIG_SMALL_SATURDAY_OFF.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_OFF,
                date(2026, 5, 9), MON_FRI_BITS, HOLIDAYS));
        // Make-up workday rings regardless of the week type.
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_OFF,
                date(2026, 4, 25), MON_FRI_BITS, HOLIDAYS));
        // Holidays stay silent.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.BIG_SMALL_SATURDAY_OFF,
                date(2026, 4, 27), MON_FRI_BITS, HOLIDAYS));
    }

    @Test
    public void weekParityIsContinuousAcrossYearBoundary() {
        // The week containing 2026-01-01 and the week containing 2025-12-31 must have opposite
        // parity so the alternation never stalls at New Year.
        final LocalDate newYearsEve = date(2025, 12, 31);
        final LocalDate newYearsDay = date(2026, 1, 1);
        assertTrue(WorkdayPolicy.isBigWeek(newYearsEve) != WorkdayPolicy.isBigWeek(newYearsDay));
    }

    @Test
    public void statutoryHonorsTheAlarmsOwnWeekdayPattern() {
        // A Monday-only pattern rings on Monday 2026-04-20...
        final int mondayBits = 0x01; // Monday
        assertTrue(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 20), mondayBits, HolidayProvider.EMPTY));
        // ...but not on Tuesday 2026-04-21.
        assertFalse(WorkdayPolicy.shouldRing(WorkdayType.STATUTORY_WORKDAY,
                date(2026, 4, 21), mondayBits, HolidayProvider.EMPTY));
    }
}
