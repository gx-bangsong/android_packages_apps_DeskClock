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
 * A saved quick timer preset: a duration and an optional label that can be started with a single
 * tap from the timer creation screen.
 */
public final class QuickTimer {

    /** Unique identifier of this preset. */
    public final long id;

    /** The duration of the preset in milliseconds; always positive. */
    public final long duration;

    /** An optional label; may be empty. */
    public final String label;

    /**
     * @param id the unique identifier of the preset
     * @param duration the duration in milliseconds; must be &gt; 0
     * @param label an optional label; must not be null (may be empty)
     */
    public QuickTimer(long id, long duration, String label) {
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.id = id;
        this.duration = duration;
        this.label = label == null ? "" : label;
    }
}
