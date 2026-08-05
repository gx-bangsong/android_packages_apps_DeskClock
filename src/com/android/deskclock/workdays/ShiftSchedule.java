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



import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * Immutable description of a shift-rotation schedule.
 *
 * <p>A shift schedule repeats every {@link #getCycleDays() cycle days}. Day 1 of the cycle is
 * {@link #getStartDate()}. Each day of the cycle can be individually enabled or disabled;
 * disabled days are rest days. Optionally, legal holidays are skipped while make-up workdays
 * still ring.</p>
 *
 * <p>All date arithmetic is performed on local dates ({@link LocalDate}) so that month and year
 * boundaries and time-zone changes are handled correctly without any wall-clock ambiguity.</p>
 */
public final class ShiftSchedule {

    /** The default cycle length used when an alarm is first switched to shift mode. */
    public static final int DEFAULT_CYCLE_DAYS = 7;

    /** The minimum allowed cycle length. */
    public static final int MIN_CYCLE_DAYS = 1;

    /** The maximum allowed cycle length. */
    public static final int MAX_CYCLE_DAYS = 365;

    private final int mCycleDays;
    private final LocalDate mStartDate;
    private final boolean mSkipHolidays;
    private final boolean[] mEnabledDays;

    /**
     * @param cycleDays   the number of days in one shift cycle; must be &gt; 0
     * @param startDate   the date of day 1 of the cycle; must not be null
     * @param skipHolidays whether legal holidays should be skipped (make-up days still ring)
     * @param enabledDays per-day enabled state; must not be null and its length must equal
     *                    {@code cycleDays}; index 0 corresponds to day 1 of the cycle
     * @throws IllegalArgumentException if any of the arguments is invalid
     */
    public ShiftSchedule(int cycleDays,  LocalDate startDate, boolean skipHolidays,
             boolean[] enabledDays) {
        if (cycleDays < MIN_CYCLE_DAYS || cycleDays > MAX_CYCLE_DAYS) {
            throw new IllegalArgumentException("cycleDays out of range: " + cycleDays);
        }
        if (startDate == null) {
            throw new IllegalArgumentException("startDate must not be null");
        }
        if (enabledDays == null || enabledDays.length != cycleDays) {
            throw new IllegalArgumentException("enabledDays length must equal cycleDays");
        }
        mCycleDays = cycleDays;
        mStartDate = startDate;
        mSkipHolidays = skipHolidays;
        mEnabledDays = enabledDays.clone();
    }

    /**
     * @return a new schedule with sane defaults: a one-week cycle starting today with every day
     *         enabled and holiday skipping turned off
     */
    public static ShiftSchedule createDefault() {
        final boolean[] enabled = new boolean[DEFAULT_CYCLE_DAYS];
        Arrays.fill(enabled, true);
        return new ShiftSchedule(DEFAULT_CYCLE_DAYS, LocalDate.now(), false, enabled);
    }

    public int getCycleDays() {
        return mCycleDays;
    }

    
    public LocalDate getStartDate() {
        return mStartDate;
    }

    public boolean isSkipHolidays() {
        return mSkipHolidays;
    }

    /**
     * @param dayIndex zero-based index of the cycle day (0 = day 1 of the cycle)
     * @return {@code true} if that cycle day is an enabled workday
     */
    public boolean isDayEnabled(int dayIndex) {
        if (dayIndex < 0 || dayIndex >= mEnabledDays.length) {
            throw new IllegalArgumentException("dayIndex out of range: " + dayIndex);
        }
        return mEnabledDays[dayIndex];
    }

    /** @return a defensive copy of the per-day enabled state (index 0 = day 1) */
    
    public boolean[] getEnabledDays() {
        return mEnabledDays.clone();
    }

    /** @return {@code true} if at least one day of the cycle is enabled */
    public boolean hasAnyEnabledDay() {
        for (boolean enabled : mEnabledDays) {
            if (enabled) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param date a date
     * @return the zero-based index of the cycle day that {@code date} falls on
     */
    public int dayIndexFor( LocalDate date) {
        final long diff = date.toEpochDay() - mStartDate.toEpochDay();
        return (int) Math.floorMod(diff, mCycleDays);
    }

    /**
     * @param date a date
     * @return the first date of the cycle that contains {@code date}
     */
    
    public LocalDate cycleStartFor( LocalDate date) {
        final long diff = date.toEpochDay() - mStartDate.toEpochDay();
        final long cycles = Math.floorDiv(diff, mCycleDays);
        return mStartDate.plusDays(cycles * mCycleDays);
    }

    /**
     * @param date a date
     * @return the last date of the cycle that contains {@code date}
     */
    
    public LocalDate cycleEndFor( LocalDate date) {
        return cycleStartFor(date).plusDays(mCycleDays - 1L);
    }

    /** @return a copy of this schedule with a new cycle length (existing days are kept where possible) */
    
    public ShiftSchedule withCycleDays(int cycleDays) {
        final boolean[] enabled = new boolean[cycleDays];
        for (int i = 0; i < cycleDays; i++) {
            enabled[i] = i < mEnabledDays.length && mEnabledDays[i];
        }
        return new ShiftSchedule(cycleDays, mStartDate, mSkipHolidays, enabled);
    }

    /** @return a copy of this schedule with a new start date */
    
    public ShiftSchedule withStartDate( LocalDate startDate) {
        return new ShiftSchedule(mCycleDays, startDate, mSkipHolidays, mEnabledDays);
    }

    /** @return a copy of this schedule with holiday skipping toggled */
    
    public ShiftSchedule withSkipHolidays(boolean skipHolidays) {
        return new ShiftSchedule(mCycleDays, mStartDate, skipHolidays, mEnabledDays);
    }

    /**
     * @param dayIndex zero-based index of the cycle day to change
     * @param enabled the new enabled state
     * @return a copy of this schedule with that cycle day changed
     */
    
    public ShiftSchedule withDayEnabled(int dayIndex, boolean enabled) {
        final boolean[] days = mEnabledDays.clone();
        if (dayIndex < 0 || dayIndex >= days.length) {
            throw new IllegalArgumentException("dayIndex out of range: " + dayIndex);
        }
        days[dayIndex] = enabled;
        return new ShiftSchedule(mCycleDays, mStartDate, mSkipHolidays, days);
    }

    /**
     * @return a string of '1' and '0' characters describing the per-day enabled state,
     *         suitable for persistent storage
     */
    
    public String getDaysMask() {
        final StringBuilder sb = new StringBuilder(mEnabledDays.length);
        for (boolean enabled : mEnabledDays) {
            sb.append(enabled ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Parses a stored days mask. Missing, null or malformed masks yield every day enabled.
     * A mask that is shorter than the cycle length is right-padded with enabled days so that a
     * schedule extended after an upgrade still works.
     *
     * @param mask the stored mask, may be null
     * @param cycleDays the cycle length the mask is expected to describe; must be &gt; 0
     * @return the parsed enabled state
     */
    
    public static boolean[] parseDaysMask(String mask, int cycleDays) {
        final boolean[] enabled = new boolean[cycleDays];
        Arrays.fill(enabled, true);
        if (mask == null) {
            return enabled;
        }
        for (int i = 0; i < cycleDays && i < mask.length(); i++) {
            final char c = mask.charAt(i);
            // Only an explicit '0' disables a day. Anything else (including malformed
            // characters) keeps the day enabled so a corrupt mask can never silence an alarm.
            if (c == '0') {
                enabled[i] = false;
            }
        }
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ShiftSchedule that = (ShiftSchedule) o;
        return mCycleDays == that.mCycleDays
                && mSkipHolidays == that.mSkipHolidays
                && mStartDate.equals(that.mStartDate)
                && Arrays.equals(mEnabledDays, that.mEnabledDays);
    }

    @Override
    public int hashCode() {
        int result = mCycleDays;
        result = 31 * result + mStartDate.hashCode();
        result = 31 * result + (mSkipHolidays ? 1 : 0);
        result = 31 * result + Arrays.hashCode(mEnabledDays);
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "ShiftSchedule{cycleDays=%d, startDate=%s, skipHolidays=%b, mask=%s}",
                mCycleDays, mStartDate, mSkipHolidays, getDaysMask());
    }
}
