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

import android.content.Context;

import com.android.deskclock.R;

/**
 * Small helpers for displaying workday types in the alarm list.
 */
public final class WorkdayTypeUtils {

    private WorkdayTypeUtils() {}

    /**
     * @param context a context
     * @param workdayType one of the {@link WorkdayType} constants
     * @return the user-facing label of the workday type
     */
    public static String getLabel(Context context, int workdayType) {
        switch (workdayType) {
            case WorkdayType.STATUTORY_WORKDAY:
                return context.getString(R.string.workday_type_statutory);
            case WorkdayType.SINGLE_DAY_OFF:
                return context.getString(R.string.workday_type_single_off);
            case WorkdayType.BIG_SMALL_SATURDAY_ON:
                return context.getString(R.string.workday_type_big_small_sun);
            case WorkdayType.BIG_SMALL_SATURDAY_OFF:
                return context.getString(R.string.workday_type_big_small_weekend);
            case WorkdayType.SHIFT:
                return context.getString(R.string.workday_type_shift);
            default:
                return context.getString(R.string.workday_type_none);
        }
    }
}
