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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Exercises the shift schedule data model: cycle math across month and year boundaries, the
 * current-period computation, the days mask and the immutable update operations.
 */
public class ShiftScheduleTest {

    private static final LocalDate START = LocalDate.of(2026, 4, 25);

    private static ShiftSchedule schedule(int cycleDays, boolean... enabled) {
        final boolean[] days = new boolean[cycleDays];
        for (int i = 0; i < cycleDays && i < enabled.length; i++) {
            days[i] = enabled[i];
        }
        return new ShiftSchedule(cycleDays, START, false, days);
    }

    @Test
    public void rejectsInvalidArguments() {
        try {
            new ShiftSchedule(0, START, false, new boolean[0]);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new ShiftSchedule(7, null, false, new boolean[7]);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new ShiftSchedule(7, START, false, new boolean[3]);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void dayIndexStartsAtZeroAndWraps() {
        final ShiftSchedule schedule = schedule(3, true, true, true);
        assertEquals(0, schedule.dayIndexFor(LocalDate.of(2026, 4, 25)));
        assertEquals(1, schedule.dayIndexFor(LocalDate.of(2026, 4, 26)));
        assertEquals(2, schedule.dayIndexFor(LocalDate.of(2026, 4, 27)));
        // Cycle wraps around.
        assertEquals(0, schedule.dayIndexFor(LocalDate.of(2026, 4, 28)));
        // Works across months.
        assertEquals(1, schedule.dayIndexFor(LocalDate.of(2026, 5, 2)));
        // Works across years: 2027-01-03 is 253 days after the start, 253 % 3 == 1.
        assertEquals(1, schedule.dayIndexFor(LocalDate.of(2027, 1, 3)));
    }

    @Test
    public void dayIndexWorksForDatesBeforeTheStartDate() {
        final ShiftSchedule schedule = schedule(9, true, true, true, true, true, true, true, true,
                true);
        // 2026-04-24 is the day before the start date: the last day of the previous cycle.
        assertEquals(8, schedule.dayIndexFor(LocalDate.of(2026, 4, 24)));
        assertEquals(7, schedule.dayIndexFor(LocalDate.of(2026, 4, 23)));
    }

    @Test
    public void currentPeriodContainsTheReferenceDate() {
        final ShiftSchedule schedule = schedule(9, true, true, true, true, true, true, true, true,
                true);
        // 2026-04-28 lies in the first cycle: 2026-04-25 .. 2026-05-03.
        assertEquals(LocalDate.of(2026, 4, 25), schedule.cycleStartFor(LocalDate.of(2026, 4, 28)));
        assertEquals(LocalDate.of(2026, 5, 3), schedule.cycleEndFor(LocalDate.of(2026, 4, 28)));
        // The same period is reported for the first day of the next cycle.
        assertEquals(LocalDate.of(2026, 5, 4), schedule.cycleStartFor(LocalDate.of(2026, 5, 4)));
        assertEquals(LocalDate.of(2026, 5, 12), schedule.cycleEndFor(LocalDate.of(2026, 5, 4)));
        // A date beyond the first cycle maps to the cycle that contains it.
        assertEquals(LocalDate.of(2026, 5, 13), schedule.cycleStartFor(LocalDate.of(2026, 5, 15)));
        assertEquals(LocalDate.of(2026, 5, 21), schedule.cycleEndFor(LocalDate.of(2026, 5, 15)));
        // Cross-year behavior: 2027-01-02 is exactly a multiple of 9 days after the start date.
        assertEquals(LocalDate.of(2027, 1, 2), schedule.cycleStartFor(LocalDate.of(2027, 1, 2)));
        assertEquals(LocalDate.of(2027, 1, 10), schedule.cycleEndFor(LocalDate.of(2027, 1, 2)));
        assertEquals(LocalDate.of(2027, 1, 2), schedule.cycleStartFor(LocalDate.of(2027, 1, 4)));
        assertEquals(LocalDate.of(2027, 1, 10), schedule.cycleEndFor(LocalDate.of(2027, 1, 4)));
    }

    @Test
    public void daysMaskRoundTrips() {
        final ShiftSchedule schedule = schedule(5, true, false, true, true, false);
        assertEquals("10110", schedule.getDaysMask());
        final boolean[] parsed = ShiftSchedule.parseDaysMask("10110", 5);
        assertArrayEquals(schedule.getEnabledDays(), parsed);
    }

    @Test
    public void malformedMasksFallBackToAllEnabled() {
        final boolean[] parsed = ShiftSchedule.parseDaysMask(null, 4);
        assertTrue(parsed[0] && parsed[1] && parsed[2] && parsed[3]);

        final boolean[] garbage = ShiftSchedule.parseDaysMask("xyz", 3);
        assertTrue(garbage[0] && garbage[1] && garbage[2]);

        // A short mask is right-padded with enabled days.
        final boolean[] shortMask = ShiftSchedule.parseDaysMask("0", 3);
        assertFalse(shortMask[0]);
        assertTrue(shortMask[1]);
        assertTrue(shortMask[2]);
    }

    @Test
    public void updatesReturnNewInstances() {
        final ShiftSchedule original = schedule(7, true, true, true, true, true, true, true);
        final ShiftSchedule changed = original.withCycleDays(9);
        assertNotEquals(original, changed);
        assertEquals(9, changed.getCycleDays());
        // Extending the cycle keeps the existing days; the new days are disabled by default so
        // the alarm never rings on days the user has not explicitly enabled.
        assertTrue(changed.isDayEnabled(6));
        assertFalse(changed.isDayEnabled(7));
        assertFalse(changed.isDayEnabled(8));
        // The original instance is untouched.
        assertEquals(7, original.getCycleDays());

        final ShiftSchedule withNewStart = original.withStartDate(LocalDate.of(2026, 5, 1));
        assertEquals(LocalDate.of(2026, 5, 1), withNewStart.getStartDate());
        assertEquals(0, withNewStart.dayIndexFor(LocalDate.of(2026, 5, 1)));

        final ShiftSchedule withDayOff = original.withDayEnabled(2, false);
        assertFalse(withDayOff.isDayEnabled(2));
        assertTrue(original.isDayEnabled(2));

        final ShiftSchedule withSkip = original.withSkipHolidays(true);
        assertTrue(withSkip.isSkipHolidays());
        assertFalse(original.isSkipHolidays());
    }

    @Test
    public void perDayTimesRoundTripAndUseFallbacks() {
        final ShiftSchedule original = schedule(3, true, true, true, true)
                .withDayTime(0, LocalTime.of(8, 35))
                .withDayTime(2, LocalTime.of(22, 5));
        assertEquals("08:35||22:05", original.getTimesMask());

        final LocalTime[] parsed = ShiftSchedule.parseTimesMask(original.getTimesMask(), 3);
        assertEquals(LocalTime.of(8, 35), parsed[0]);
        assertNull(parsed[1]);
        assertEquals(LocalTime.of(22, 5), parsed[2]);
        assertEquals(LocalTime.of(7, 0), original.getDayTime(1, LocalTime.of(7, 0)));
    }

    @Test
    public void hasAnyEnabledDay() {
        assertTrue(schedule(3, true, false, false).hasAnyEnabledDay());
        assertFalse(schedule(3, false, false, false).hasAnyEnabledDay());
    }

    @Test
    public void shrinkingACycleDropsTrailingDays() {
        final ShiftSchedule original = schedule(9, true, true, true, true, true, true, true, false,
                false);
        final ShiftSchedule smaller = original.withCycleDays(5);
        assertEquals(5, smaller.getCycleDays());
        assertTrue(smaller.isDayEnabled(4));
    }
}
