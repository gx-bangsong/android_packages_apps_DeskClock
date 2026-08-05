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

/**
 * Constants describing the workday types that may be assigned to an alarm.
 *
 * <p>The type {@link #NONE} is the default; alarms with that type behave exactly like the
 * original DeskClock alarms. All other types refine the schedule by skipping legal holidays,
 * ringing on make-up (compensation) workdays or by switching to a shift-rotation schedule.</p>
 */
public final class WorkdayType {

    /** No workday logic; the alarm follows the plain weekly/one-time schedule. */
    public static final int NONE = 0;

    /** 法定工作日: ring on the alarm's selected weekdays, skip legal holidays, ring on make-up days. */
    public static final int STATUTORY_WORKDAY = 1;

    /** 单休制（仅休周日）: work Monday through Saturday, rest Sunday. */
    public static final int SINGLE_DAY_OFF = 2;

    /**
     * 大小周制（本周休周日）: alternating weeks. In "big" weeks (this week) only Sunday is off;
     * in "small" weeks both Saturday and Sunday are off.
     */
    public static final int BIG_SMALL_SATURDAY_ON = 3;

    /**
     * 大小周制（本周休周六、日）: alternating weeks. In "small" weeks (this week) both Saturday
     * and Sunday are off; in "big" weeks only Sunday is off.
     */
    public static final int BIG_SMALL_SATURDAY_OFF = 4;

    /** 轮班制: the alarm follows a shift-rotation schedule instead of a weekly schedule. */
    public static final int SHIFT = 5;

    /** The number of distinct workday types (exclusive of {@link #NONE}). */
    public static final int COUNT = 5;

    private WorkdayType() {}
}
