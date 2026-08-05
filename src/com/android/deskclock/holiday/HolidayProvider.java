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

/**
 * Provides holiday information for the workday and shift-rotation scheduling logic.
 *
 * <p>Implementations must be safe to call from any thread. The default implementation backed by
 * {@link HolidayRepository} never throws; it simply reports no holiday for unknown dates, so an
 * unavailable holiday dataset can never crash the alarm scheduling.</p>
 */
public interface HolidayProvider {

    /**
     * @param date the date to check
     * @return {@code true} if {@code date} is part of a legal (statutory) holiday period
     */
    boolean isLegalHoliday(LocalDate date);

    /**
     * @param date the date to check
     * @return {@code true} if {@code date} is a make-up (compensation) workday, i.e. a day that
     *         would normally be a weekend but is a workday because of a nearby holiday
     */
    boolean isCompWorkday(LocalDate date);

    /** A provider that reports no holidays at all; used when no data is available. */
    HolidayProvider EMPTY = new HolidayProvider() {
        @Override
        public boolean isLegalHoliday(LocalDate date) {
            return false;
        }

        @Override
        public boolean isCompWorkday(LocalDate date) {
            return false;
        }
    };
}
