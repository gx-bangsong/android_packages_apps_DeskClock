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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single point of access to the quick timer presets. Presets are persisted in the
 * device-protected shared preferences (the same store used by the rest of the app, which is
 * included in application backups).
 */
public final class QuickTimerRepository {

    private static volatile QuickTimerRepository sInstance;

    private final SharedPreferences mPrefs;
    private final List<QuickTimer> mQuickTimers = new ArrayList<>();
    private final List<QuickTimerListener> mListeners = new ArrayList<>();

    /** Listener notified whenever the set of quick timer presets changes. */
    public interface QuickTimerListener {
        void onQuickTimersChanged(List<QuickTimer> quickTimers);
    }

    private QuickTimerRepository(Context context) {
        // Use the device-protected shared preferences, the same store used by the rest of the
        // app (see DeskClockApplication) which is included in application backups.
        final Context storageContext = context.createDeviceProtectedStorageContext();
        mPrefs = storageContext.getSharedPreferences(
                PreferenceManager.getDefaultSharedPreferencesName(storageContext),
                Context.MODE_PRIVATE);
        mQuickTimers.addAll(QuickTimerDAO.getQuickTimers(mPrefs));
    }

    /**
     * Must be called from {@code DeskClockApplication#onCreate} before any other access.
     */
    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (QuickTimerRepository.class) {
                if (sInstance == null) {
                    sInstance = new QuickTimerRepository(context);
                }
            }
        }
    }

    /** @return the shared repository instance; must not be called before {@link #init} */
    public static QuickTimerRepository getInstance() {
        final QuickTimerRepository instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException("QuickTimerRepository is not initialized");
        }
        return instance;
    }

    /** @return an unmodifiable snapshot of the current presets */
    public List<QuickTimer> getQuickTimers() {
        return Collections.unmodifiableList(new ArrayList<>(mQuickTimers));
    }

    /**
     * Adds a new preset and persists it.
     *
     * @param duration the duration in milliseconds; must be &gt; 0
     * @param label the label, may be empty
     */
    public void addQuickTimer(long duration, String label) {
        final QuickTimer quickTimer = QuickTimerDAO.addQuickTimer(mPrefs, duration, label);
        mQuickTimers.add(quickTimer);
        notifyListeners();
    }

    /**
     * Deletes a preset and persists the deletion.
     *
     * @param quickTimer the preset to delete
     */
    public void deleteQuickTimer(QuickTimer quickTimer) {
        QuickTimerDAO.deleteQuickTimer(mPrefs, quickTimer.id);
        mQuickTimers.remove(quickTimer);
        notifyListeners();
    }

    public void addListener(QuickTimerListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(QuickTimerListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        final List<QuickTimer> snapshot = getQuickTimers();
        for (QuickTimerListener listener : mListeners) {
            listener.onQuickTimersChanged(snapshot);
        }
    }
}
