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

package com.android.deskclock.timer.quick;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Exercises the quick timer duration validation.
 */
public class QuickTimerDurationTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    public void parsesSimpleDurations() {
        assertEquals(10 * MINUTE, QuickTimerDuration.toMillis("0", "10", "0"));
        assertEquals(90 * MINUTE, QuickTimerDuration.toMillis("1", "30", "0"));
        assertEquals(5_000L, QuickTimerDuration.toMillis("", "", "5"));
        assertEquals(HOUR + 2 * MINUTE + 3_000L,
                QuickTimerDuration.toMillis("1", "2", "3"));
    }

    @Test
    public void emptyFieldsAreZero() {
        assertEquals(MINUTE, QuickTimerDuration.toMillis(null, "1", null));
        assertEquals(MINUTE, QuickTimerDuration.toMillis("", "1", ""));
    }

    @Test
    public void rejectsZeroDuration() {
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("", "", ""));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("0", "0", "0"));
    }

    @Test
    public void rejectsNonNumericInput() {
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("a", "1", "0"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("1", "b", "0"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("1", "1", "c"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("1.5", "0", "0"));
    }

    @Test
    public void rejectsNegativeInput() {
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("-1", "0", "0"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("0", "-5", "0"));
    }

    @Test
    public void rejectsOutOfRangeFields() {
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("100", "0", "0"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("0", "60", "0"));
        assertEquals(QuickTimerDuration.INVALID, QuickTimerDuration.toMillis("0", "0", "60"));
    }

    @Test
    public void acceptsMaximumDuration() {
        assertEquals(99 * HOUR + 59 * MINUTE + 59_000L,
                QuickTimerDuration.toMillis("99", "59", "59"));
    }

    @Test
    public void whitespaceIsTolerated() {
        assertEquals(MINUTE, QuickTimerDuration.toMillis(" ", " 1 ", "  "));
    }
}
