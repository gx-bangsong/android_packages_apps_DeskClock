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

package com.android.deskclock.holiday;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single holiday entry as provided by a holiday data source.
 *
 * <p>An entry covers the inclusive date range {@code [startDate, endDate]} (a single-day holiday
 * has {@code startDate == endDate}). {@code compDays} lists the make-up (compensation) workdays
 * that belong to this holiday, e.g. a weekend day that becomes a workday to compensate for the
 * holiday.</p>
 */
public final class Holiday {

    private final String mName;
    private final LocalDate mStartDate;
    private final LocalDate mEndDate;
    private final List<LocalDate> mCompDays;

    /**
     * @param name the display name of the holiday, may be empty
     * @param startDate the first day of the holiday, inclusive
     * @param endDate the last day of the holiday, inclusive; must not precede {@code startDate}
     * @param compDays make-up workdays, may be empty; entries outside the holiday range are kept
     *                 as-is (data sources usually place make-up days adjacent to the holiday)
     */
    public Holiday(String name, LocalDate startDate, LocalDate endDate, List<LocalDate> compDays) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.toEpochDay() < startDate.toEpochDay()) {
            throw new IllegalArgumentException("endDate must not precede startDate");
        }
        mName = name == null ? "" : name;
        mStartDate = startDate;
        mEndDate = endDate;
        mCompDays = compDays == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(compDays));
    }

    public String getName() {
        return mName;
    }

    public LocalDate getStartDate() {
        return mStartDate;
    }

    public LocalDate getEndDate() {
        return mEndDate;
    }

    /** @return an unmodifiable list of make-up workdays */
    public List<LocalDate> getCompDays() {
        return mCompDays;
    }

    /**
     * @param date a date
     * @return {@code true} if {@code date} lies within the holiday range
     */
    public boolean covers(LocalDate date) {
        return date.toEpochDay() >= mStartDate.toEpochDay()
                && date.toEpochDay() <= mEndDate.toEpochDay();
    }

    /**
     * @param date a date
     * @return {@code true} if {@code date} is a make-up workday of this holiday
     */
    public boolean isCompWorkday(LocalDate date) {
        return mCompDays.contains(date);
    }

    @Override
    public String toString() {
        return "Holiday{name='" + mName + "', startDate=" + mStartDate + ", endDate=" + mEndDate
                + ", compDays=" + mCompDays + '}';
    }
}
