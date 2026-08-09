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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Parses holiday JSON documents into {@link Holiday} objects.
 *
 * <p>Two shapes are accepted:</p>
 * <ul>
 *   <li>a top-level JSON array of holiday objects, or</li>
 *   <li>a JSON object containing a {@code "holidays"} (or {@code "data"}) array, or the
 *       year-indexed {@code "Years"} object used by the default China holiday API.</li>
 * </ul>
 *
 * <p>Each holiday object may use either {@code startDate}/{@code endDate} or the underscore
 * variants {@code start_date}/{@code end_date}; the end date defaults to the start date. Make-up
 * workdays are read from {@code compDays} (or {@code comp_days}) and may be a JSON array of date
 * strings or a single date string. Dates are expected in {@code yyyy-MM-dd} form; single-digit
 * month/day values are tolerated.</p>
 *
 * <p>The parser is lenient about individual malformed entries (they are skipped) but throws
 * {@link JSONException} for structurally invalid documents so that callers can keep their last
 * valid dataset.</p>
 */
public final class HolidayDataParser {

    private HolidayDataParser() {}

    /**
     * @param json the document to parse
     * @return the parsed holidays; may be empty
     * @throws JSONException if the document is not a valid holiday document
     */
    public static List<Holiday> parse(String json) throws JSONException {
        final Object root = readRoot(json);
        final List<JSONArray> arrays = new ArrayList<>();
        if (root instanceof JSONArray) {
            arrays.add((JSONArray) root);
        } else if (root instanceof JSONObject) {
            final JSONObject object = (JSONObject) root;
            final JSONArray directArray = firstArray(object, "holidays", "data");
            if (directArray != null) {
                arrays.add(directArray);
            } else {
                // The default China holiday API stores entries under a Years object:
                // { "Years": { "2026": [ ... ], "2025": [ ... ] } }.
                final JSONObject years = firstObject(object, "Years", "years");
                if (years != null) {
                    final Iterator<String> keys = years.keys();
                    while (keys.hasNext()) {
                        final Object value = years.opt(keys.next());
                        if (value instanceof JSONArray) {
                            arrays.add((JSONArray) value);
                        }
                    }
                }
            }
        }
        if (arrays.isEmpty()) {
            throw new JSONException("JSON document must contain a holiday array");
        }

        final List<Holiday> holidays = new ArrayList<>();
        for (JSONArray array : arrays) {
            for (int i = 0; i < array.length(); i++) {
                final Object value = array.opt(i);
                if (!(value instanceof JSONObject)) {
                    continue;
                }
                final Holiday holiday = parseHoliday((JSONObject) value);
                if (holiday != null) {
                    holidays.add(holiday);
                }
            }
        }
        return holidays;
    }

    private static Object readRoot(String json) throws JSONException {
        if (json == null) {
            throw new JSONException("JSON document is null");
        }
        for (int i = 0; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '\ufeff' || Character.isWhitespace(c)) {
                continue;
            }
            if (c == '{') {
                return new JSONObject(json);
            }
            if (c == '[') {
                return new JSONArray(json);
            }
            throw new JSONException("JSON document must be an array or an object");
        }
        throw new JSONException("JSON document is empty");
    }

    private static Holiday parseHoliday(JSONObject object) {
        try {
            final String name = firstString(object, "name", "Name", "title", "holiday");
            final String startText = firstString(object, "startDate", "StartDate",
                    "start_date", "date");
            if (startText == null) {
                return null;
            }
            final LocalDate startDate = parseDate(startText);
            final String endText = firstString(object, "endDate", "EndDate", "end_date");
            final LocalDate endDate = endText == null ? startDate : parseDate(endText);
            if (endDate.toEpochDay() < startDate.toEpochDay()) {
                return null;
            }

            final List<LocalDate> compDays = new ArrayList<>();
            final JSONArray compArray = firstArray(object, "compDays", "CompDays", "comp_days",
                    "compensationDays", "makeupDays");
            if (compArray != null) {
                for (int i = 0; i < compArray.length(); i++) {
                    final Object comp = compArray.opt(i);
                    if (comp instanceof String) {
                        addDate(compDays, (String) comp);
                    }
                }
            } else {
                final String singleComp = firstString(object, "compDays", "CompDays", "comp_days");
                if (singleComp != null) {
                    addDate(compDays, singleComp);
                }
            }

            return new Holiday(name == null ? "" : name, startDate, endDate, compDays);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void addDate(List<LocalDate> compDays, String text) {
        try {
            compDays.add(parseDate(text));
        } catch (IllegalArgumentException e) {
            // Ignore malformed make-up dates.
        }
    }

    /**
     * Parses a date, tolerating non-zero-padded months and days.
     *
     * @param text a date in {@code yyyy-MM-dd} form
     * @return the parsed date
     * @throws IllegalArgumentException if the text is not a valid date
     */
    public static LocalDate parseDate(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null date");
        }
        final String trimmed = text.trim();
        final String[] parts = trimmed.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid date: " + text);
        }
        try {
            final int year = Integer.parseInt(parts[0]);
            final int month = Integer.parseInt(parts[1]);
            final int day = Integer.parseInt(parts[2]);
            return LocalDate.of(year, month, day);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid date: " + text, e);
        }
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.optString(key, null);
            }
        }
        return null;
    }

    private static JSONArray firstArray(JSONObject object, String... keys) {
        for (String key : keys) {
            final Object value = object.opt(key);
            if (value instanceof JSONArray) {
                return (JSONArray) value;
            }
        }
        return null;
    }

    private static JSONObject firstObject(JSONObject object, String... keys) {
        for (String key : keys) {
            final Object value = object.opt(key);
            if (value instanceof JSONObject) {
                return (JSONObject) value;
            }
        }
        return null;
    }
}
