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

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class encapsulates the transfer of {@link QuickTimer} presets to and from their permanent
 * storage in {@link SharedPreferences}, following the same pattern used by the timer DAO.
 */
final class QuickTimerDAO {

    /** Key to a preference that stores the set of quick timer ids. */
    private static final String QUICK_TIMER_IDS = "quick_timer_ids";

    /** Key to a preference that stores the id to assign to the next quick timer. */
    private static final String NEXT_QUICK_TIMER_ID = "next_quick_timer_id";

    /** Prefix for a key to a preference that stores the duration of a quick timer. */
    private static final String DURATION = "quick_timer_duration_";

    /** Prefix for a key to a preference that stores the label of a quick timer. */
    private static final String LABEL = "quick_timer_label_";

    private QuickTimerDAO() {}

    /**
     * @param prefs the preferences to read
     * @return the quick timers from permanent storage
     */
    static List<QuickTimer> getQuickTimers(SharedPreferences prefs) {
        final Set<String> ids = prefs.getStringSet(QUICK_TIMER_IDS, Collections.emptySet());
        final List<QuickTimer> quickTimers = new ArrayList<>(ids.size());
        for (String idText : ids) {
            final long id;
            try {
                id = Long.parseLong(idText);
            } catch (NumberFormatException e) {
                continue;
            }
            final long duration = prefs.getLong(DURATION + id, 0L);
            if (duration <= 0L) {
                continue;
            }
            final String label = prefs.getString(LABEL + id, "");
            quickTimers.add(new QuickTimer(id, duration, label));
        }
        return quickTimers;
    }

    /**
     * @param prefs the preferences to write
     * @param duration the duration of the preset in milliseconds; must be &gt; 0
     * @param label the label of the preset, may be empty
     * @return the stored preset with its generated id
     */
    static QuickTimer addQuickTimer(SharedPreferences prefs, long duration, String label) {
        final SharedPreferences.Editor editor = prefs.edit();

        final long id = prefs.getLong(NEXT_QUICK_TIMER_ID, 0L);
        editor.putLong(NEXT_QUICK_TIMER_ID, id + 1L);

        final Set<String> ids = new HashSet<>(getQuickTimerIds(prefs));
        ids.add(String.valueOf(id));
        editor.putStringSet(QUICK_TIMER_IDS, ids);

        editor.putLong(DURATION + id, duration);
        editor.putString(LABEL + id, label == null ? "" : label);

        editor.apply();
        return new QuickTimer(id, duration, label == null ? "" : label);
    }

    /**
     * @param prefs the preferences to write
     * @param id the id of the preset to remove
     */
    static void deleteQuickTimer(SharedPreferences prefs, long id) {
        final SharedPreferences.Editor editor = prefs.edit();

        final Set<String> ids = new HashSet<>(getQuickTimerIds(prefs));
        ids.remove(String.valueOf(id));
        if (ids.isEmpty()) {
            editor.remove(QUICK_TIMER_IDS);
            editor.remove(NEXT_QUICK_TIMER_ID);
        } else {
            editor.putStringSet(QUICK_TIMER_IDS, ids);
        }

        editor.remove(DURATION + id);
        editor.remove(LABEL + id);

        editor.apply();
    }

    private static Set<String> getQuickTimerIds(SharedPreferences prefs) {
        return prefs.getStringSet(QUICK_TIMER_IDS, Collections.emptySet());
    }
}
