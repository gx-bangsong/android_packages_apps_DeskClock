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

/**
 * Parses and validates the hours/minutes/seconds input of the quick timer setup dialog.
 */
public final class QuickTimerDuration {

    /** Sentinel returned for invalid input (including a zero-length timer). */
    public static final long INVALID = -1L;

    /** The maximum number of hours accepted. */
    public static final int MAX_HOURS = 99;

    private QuickTimerDuration() {}

    /**
     * @param hoursText the hours field, may be empty
     * @param minutesText the minutes field, may be empty
     * @param secondsText the seconds field, may be empty
     * @return the duration in milliseconds, or {@link #INVALID} if any field is not a valid
     *         non-negative integer, a field is out of range, or the total duration is zero
     */
    public static long toMillis(String hoursText, String minutesText, String secondsText) {
        final long hours = parseField(hoursText);
        if (hours < 0 || hours > MAX_HOURS) {
            return INVALID;
        }
        final long minutes = parseField(minutesText);
        if (minutes < 0 || minutes > 59) {
            return INVALID;
        }
        final long seconds = parseField(secondsText);
        if (seconds < 0 || seconds > 59) {
            return INVALID;
        }
        final long totalSeconds = hours * 3600 + minutes * 60 + seconds;
        if (totalSeconds <= 0) {
            return INVALID;
        }
        return totalSeconds * 1000L;
    }

    /**
     * @param text a text field; null or empty is treated as zero
     * @return the parsed value, or -1 if the text is not a non-negative integer
     */
    private static long parseField(String text) {
        if (text == null) {
            return 0;
        }
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            final long value = Long.parseLong(trimmed);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
