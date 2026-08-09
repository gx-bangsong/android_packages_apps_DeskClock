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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.android.deskclock.LogUtils;
import com.android.deskclock.provider.ClockContract;
import com.android.deskclock.provider.ClockDatabaseHelper;
import com.android.deskclock.workdays.WorkdayAlarmScheduler;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stores and serves the holiday dataset used by the workday and shift-rotation scheduling logic.
 *
 * <p>The dataset is downloaded from a user-configurable URL, parsed by {@link HolidayDataParser}
 * and persisted in the {@code holiday} table of the app database. A failed download, a malformed
 * document or a database failure never destroys the previous dataset: the update keeps the last
 * valid data and simply reports the failure. A successful update reschedules all affected alarms
 * so that the new data takes effect immediately.</p>
 *
 * <p>All queries are served from an in-memory cache, so the scheduling logic (which runs on
 * background threads) never performs database I/O.</p>
 */
public final class HolidayRepository implements HolidayProvider {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("HolidayRepository");

    /** Default data source: the China holiday calendar (legal holidays + make-up workdays). */
    public static final String DEFAULT_HOLIDAY_DATA_URL =
            "https://raw.githubusercontent.com/lanceliao/china-holiday-calender/master/holidayAPI.json";

    private static final String PREF_HOLIDAY_DATA_URL = "holiday_data_url";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private static volatile HolidayRepository sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /** In-memory cache of the holiday dataset. */
    private volatile List<Holiday> mHolidays = Collections.emptyList();

    /** Callback for {@link #updateHolidayData(UpdateCallback)}. */
    public interface UpdateCallback {
        /**
         * @param success {@code true} if the dataset was replaced successfully
         * @param errorMessage a user-presentable error message when {@code success} is false
         */
        void onFinished(boolean success, String errorMessage);
    }

    private HolidayRepository(Context context) {
        mContext = context.getApplicationContext();
        final Context storageContext = mContext.createDeviceProtectedStorageContext();
        mPrefs = storageContext.getSharedPreferences(
                PreferenceManager.getDefaultSharedPreferencesName(storageContext),
                Context.MODE_PRIVATE);
        mHolidays = loadFromDatabase();
    }

    /**
     * Initializes the repository. Must be called from {@code DeskClockApplication#onCreate}
     * before the scheduling logic may use holiday data.
     */
    public static synchronized void init(Context context) {
        if (sInstance == null) {
            sInstance = new HolidayRepository(context);
        }
    }

    /**
     * @return the shared repository, or {@code null} if {@link #init} has not been called yet
     */
    public static synchronized HolidayRepository getInstanceOrNull() {
        return sInstance;
    }

    /**
     * @return the holiday provider used by the scheduling logic; never null, so scheduling can
     *         never crash on missing holiday data
     */
    public static HolidayProvider getProvider() {
        final HolidayRepository instance = getInstanceOrNull();
        return instance != null ? instance : HolidayProvider.EMPTY;
    }

    /** @return the configured holiday data URL */
    public String getHolidayDataUrl() {
        return mPrefs.getString(PREF_HOLIDAY_DATA_URL, DEFAULT_HOLIDAY_DATA_URL);
    }

    /**
     * Stores the holiday data URL. The empty value restores the default data source.
     *
     * @param url the new URL
     */
    public void setHolidayDataUrl(String url) {
        final String value = url == null || url.trim().isEmpty()
                ? DEFAULT_HOLIDAY_DATA_URL : url.trim();
        mPrefs.edit().putString(PREF_HOLIDAY_DATA_URL, value).apply();
    }

    @Override
    public boolean isLegalHoliday(LocalDate date) {
        for (Holiday holiday : mHolidays) {
            if (holiday.covers(date)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCompWorkday(LocalDate date) {
        for (Holiday holiday : mHolidays) {
            if (holiday.isCompWorkday(date)) {
                return true;
            }
        }
        return false;
    }

    /** @return an unmodifiable snapshot of the current dataset */
    public List<Holiday> getAllHolidays() {
        return Collections.unmodifiableList(new ArrayList<>(mHolidays));
    }

    /**
     * Fetches, parses and stores the holiday dataset from the configured URL on a background
     * thread. On success all alarms that use workday logic are rescheduled. On failure the
     * previous dataset is kept untouched.
     *
     * @param callback invoked on the main thread when the update finishes; may be null
     */
    public void updateHolidayData(final UpdateCallback callback) {
        mExecutor.execute(() -> {
            boolean success = false;
            String errorMessage = null;
            try {
                replaceFromJson(fetch(getHolidayDataUrl()));
                success = true;
            } catch (Exception e) {
                LOGGER.e("Failed to update holiday data", e);
                errorMessage = e.getMessage();
            }
            postUpdateResult(callback, success, errorMessage);
        });
    }

    /**
     * Imports a JSON document selected by the user with the system file picker. The selected URI
     * is read on the repository executor, so file-provider and cloud-document reads never block
     * the settings screen.
     *
     * @param uri a readable JSON document URI
     * @param callback invoked on the main thread when the import finishes
     */
    public void importHolidayData(final Uri uri, final UpdateCallback callback) {
        mExecutor.execute(() -> {
            boolean success = false;
            String errorMessage = null;
            try {
                if (uri == null) {
                    throw new IOException("No file was selected");
                }
                try (InputStream input = mContext.getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        throw new IOException("Unable to open the selected file");
                    }
                    replaceFromJson(readJson(input));
                }
                success = true;
            } catch (Exception e) {
                LOGGER.e("Failed to import holiday data", e);
                errorMessage = e.getMessage();
            }
            postUpdateResult(callback, success, errorMessage);
        });
    }

    /** Parses, stores and activates a complete holiday document. */
    private void replaceFromJson(String json) throws Exception {
        final List<Holiday> parsed = HolidayDataParser.parse(json);
        replaceAll(parsed);
        mHolidays = Collections.unmodifiableList(new ArrayList<>(parsed));
        LOGGER.i("Holiday data updated: %d entries", parsed.size());
        WorkdayAlarmScheduler.rescheduleAllWorkdayAlarms(mContext);
    }

    private void postUpdateResult(UpdateCallback callback, boolean success, String errorMessage) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onFinished(success, errorMessage));
        }
    }

    private String fetch(String urlText) throws IOException {
        final URL url = new URL(urlText);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setInstanceFollowRedirects(true);
            final int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP error " + responseCode);
            }
            try (InputStream input = connection.getInputStream()) {
                return readJson(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String readJson(InputStream input) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            final char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                if (builder.length() > MAX_RESPONSE_BYTES) {
                    throw new IOException("Holiday data exceeds "
                            + MAX_RESPONSE_BYTES + " bytes");
                }
            }
        }
        return builder.toString();
    }

    private List<Holiday> loadFromDatabase() {
        final List<Holiday> holidays = new ArrayList<>();
        final SQLiteDatabase db = getWritableDatabase();
        try (Cursor cursor = db.query(ClockDatabaseHelper.HOLIDAY_TABLE_NAME,
                new String[] {
                        ClockContract.HolidayColumns.NAME,
                        ClockContract.HolidayColumns.START_DATE,
                        ClockContract.HolidayColumns.END_DATE,
                        ClockContract.HolidayColumns.COMP_DAYS,
                },
                null, null, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    final Holiday holiday = readHoliday(cursor);
                    if (holiday != null) {
                        holidays.add(holiday);
                    }
                }
            }
        } catch (Exception e) {
            // A damaged holiday table must never prevent the app (or alarm scheduling) from
            // working; start with an empty dataset instead.
            LOGGER.e("Failed to load holiday data", e);
            return new ArrayList<>();
        }
        return holidays;
    }

    private Holiday readHoliday(Cursor cursor) {
        try {
            final String name = cursor.getString(0);
            final LocalDate startDate = HolidayDataParser.parseDate(cursor.getString(1));
            final LocalDate endDate = HolidayDataParser.parseDate(cursor.getString(2));
            final List<LocalDate> compDays = parseCompDays(cursor.getString(3));
            return new Holiday(name, startDate, endDate, compDays);
        } catch (Exception e) {
            LOGGER.e("Skipping invalid holiday row", e);
            return null;
        }
    }

    private static List<LocalDate> parseCompDays(String json) {
        final List<LocalDate> compDays = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return compDays;
        }
        try {
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final String text = array.optString(i, null);
                if (text != null) {
                    try {
                        compDays.add(HolidayDataParser.parseDate(text));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed entries.
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.e("Failed to parse stored make-up days", e);
        }
        return compDays;
    }

    /**
     * Replaces the whole dataset inside a single transaction. An exception here leaves the
     * previous dataset untouched.
     */
    private void replaceAll(List<Holiday> holidays) {
        final SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(ClockDatabaseHelper.HOLIDAY_TABLE_NAME, null, null);
            for (Holiday holiday : holidays) {
                final ContentValues values = new ContentValues();
                values.put(ClockContract.HolidayColumns.NAME, holiday.getName());
                values.put(ClockContract.HolidayColumns.START_DATE,
                        holiday.getStartDate().toString());
                values.put(ClockContract.HolidayColumns.END_DATE,
                        holiday.getEndDate().toString());
                values.put(ClockContract.HolidayColumns.COMP_DAYS, compDaysToJson(holiday));
                db.insert(ClockDatabaseHelper.HOLIDAY_TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String compDaysToJson(Holiday holiday) {
        final JSONArray array = new JSONArray();
        for (LocalDate date : holiday.getCompDays()) {
            array.put(date.toString());
        }
        return array.toString();
    }

    private SQLiteDatabase getWritableDatabase() {
        return ClockDatabaseHelper.getInstance(mContext).getWritableDatabase();
    }

    /** Utility used by tests to reset the singleton. */
    public static void resetForTest() {
        sInstance = null;
    }
}
