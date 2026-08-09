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

package com.android.deskclock.provider;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Database schema regression test.
 * Runs during Gradle build (./gradlew test) and guarantees that
 * workday_type / shift_* columns and holiday table exist after upgrade.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ClockDatabaseHelperTest {

    private Context context;
    private ClockDatabaseHelper helper;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        helper = new ClockDatabaseHelper(context);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    public void testWorkdayColumnsExistAfterUpgrade() {
        SQLiteDatabase db = helper.getWritableDatabase();

        // Verify columns on alarm_templates
        Set<String> columns = getTableColumns(db, ClockDatabaseHelper.ALARMS_TABLE_NAME);
        assertTrue("workday_type column missing", columns.contains("workday_type"));
        assertTrue("shift_cycle_days column missing", columns.contains("shift_cycle_days"));
        assertTrue("shift_start_date column missing", columns.contains("shift_start_date"));
        assertTrue("shift_skip_holiday column missing", columns.contains("shift_skip_holiday"));
        assertTrue("shift_days_mask column missing", columns.contains("shift_days_mask"));

        // Verify holiday table exists
        assertTrue("holiday table missing",
                tableExists(db, ClockDatabaseHelper.HOLIDAY_TABLE_NAME));
    }

    private Set<String> getTableColumns(SQLiteDatabase db, String tableName) {
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    cols.add(c.getString(1)); // column name is index 1
                }
            }
        }
        return cols;
    }

    private boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName})) {
            return c != null && c.moveToFirst();
        }
    }
}
