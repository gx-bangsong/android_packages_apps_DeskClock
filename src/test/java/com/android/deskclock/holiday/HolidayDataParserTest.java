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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Exercises the holiday JSON parser: the two supported document shapes, make-up workday
 * variants, date tolerance and structural validation.
 */
public class HolidayDataParserTest {

    @Test
    public void parsesArrayDocument() throws JSONException {
        final String json = "["
                + "{\"name\":\"元旦\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\","
                + "\"compDays\":[\"2026-01-04\"]},"
                + "{\"name\":\"劳动节\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-05\","
                + "\"compDays\":[\"2026-04-25\",\"2026-05-09\"]}"
                + "]";
        final List<Holiday> holidays = HolidayDataParser.parse(json);
        assertEquals(2, holidays.size());

        final Holiday newYear = holidays.get(0);
        assertEquals("元旦", newYear.getName());
        assertEquals(LocalDate.of(2026, 1, 1), newYear.getStartDate());
        assertEquals(LocalDate.of(2026, 1, 3), newYear.getEndDate());
        assertEquals(1, newYear.getCompDays().size());
        assertTrue(newYear.isCompWorkday(LocalDate.of(2026, 1, 4)));
        assertTrue(newYear.covers(LocalDate.of(2026, 1, 2)));
        assertFalse(newYear.covers(LocalDate.of(2026, 1, 5)));

        final Holiday laborDay = holidays.get(1);
        assertEquals(2, laborDay.getCompDays().size());
        assertTrue(laborDay.isCompWorkday(LocalDate.of(2026, 4, 25)));
        assertTrue(laborDay.isCompWorkday(LocalDate.of(2026, 5, 9)));
    }

    @Test
    public void parsesYearIndexedChinaHolidayDocument() throws JSONException {
        final String json = "{\"Years\":{\"2026\":["
                + "{\"Name\":\"元旦\",\"StartDate\":\"2026-01-01\","
                + "\"EndDate\":\"2026-01-03\",\"CompDays\":[\"2026-01-04\"]}"
                + "]}}";
        final List<Holiday> holidays = HolidayDataParser.parse(json);
        assertEquals(1, holidays.size());
        assertEquals("元旦", holidays.get(0).getName());
        assertTrue(holidays.get(0).isCompWorkday(LocalDate.of(2026, 1, 4)));
    }

    @Test
    public void parsesObjectDocument() throws JSONException {
        final String json = "{\"code\":200,\"holidays\":["
                + "{\"name\":\"国庆节\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-07\"}"
                + "]}";
        final List<Holiday> holidays = HolidayDataParser.parse(json);
        assertEquals(1, holidays.size());
        assertEquals("国庆节", holidays.get(0).getName());
        // The end date defaults to the start date when absent.
        final String singleDay = "[{\"name\":\"清明\",\"date\":\"2026-04-05\"}]";
        final Holiday qingMing = HolidayDataParser.parse(singleDay).get(0);
        assertEquals(LocalDate.of(2026, 4, 5), qingMing.getStartDate());
        assertEquals(LocalDate.of(2026, 4, 5), qingMing.getEndDate());
    }

    @Test
    public void acceptsUnderscoreKeysAndSingleCompDay() throws JSONException {
        final String json = "[{\"name\":\"春节\",\"start_date\":\"2026-02-16\","
                + "\"end_date\":\"2026-02-22\",\"comp_days\":\"2026-02-15\"}]";
        final List<Holiday> holidays = HolidayDataParser.parse(json);
        assertEquals(1, holidays.size());
        assertTrue(holidays.get(0).isCompWorkday(LocalDate.of(2026, 2, 15)));
    }

    @Test
    public void toleratesNonZeroPaddedDates() throws JSONException {
        final List<Holiday> holidays = HolidayDataParser.parse(
                "[{\"name\":\"x\",\"startDate\":\"2026-1-2\",\"endDate\":\"2026-01-04\"}]");
        assertEquals(LocalDate.of(2026, 1, 2), holidays.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 1, 4), holidays.get(0).getEndDate());
    }

    @Test
    public void skipsMalformedEntriesButKeepsValidOnes() throws JSONException {
        final String json = "["
                + "{\"name\":\"ok\",\"startDate\":\"2026-05-01\"},"
                + "{\"name\":\"bad-date\",\"startDate\":\"not-a-date\"},"
                + "{\"name\":\"reversed\",\"startDate\":\"2026-05-10\",\"endDate\":\"2026-05-01\"},"
                + "\"not-an-object\","
                + "42,"
                + "{\"name\":\"no-date\"}"
                + "]";
        final List<Holiday> holidays = HolidayDataParser.parse(json);
        assertEquals(1, holidays.size());
        assertEquals("ok", holidays.get(0).getName());
    }

    @Test
    public void emptyArrayIsValidAndEmpty() throws JSONException {
        assertTrue(HolidayDataParser.parse("[]").isEmpty());
        assertTrue(HolidayDataParser.parse("{\"holidays\":[]}").isEmpty());
    }

    @Test(expected = JSONException.class)
    public void garbageDocumentThrows() throws JSONException {
        HolidayDataParser.parse("this is not json {");
    }

    @Test(expected = JSONException.class)
    public void nullDocumentThrows() throws JSONException {
        HolidayDataParser.parse(null);
    }

    @Test(expected = JSONException.class)
    public void objectWithoutHolidaysArrayThrows() throws JSONException {
        HolidayDataParser.parse("{\"foo\":1}");
    }

    @Test(expected = JSONException.class)
    public void emptyDocumentThrows() throws JSONException {
        HolidayDataParser.parse("   ");
    }

    @Test
    public void dataArrayIsAccepted() throws JSONException {
        final List<Holiday> holidays = HolidayDataParser.parse(
                "{\"data\":[{\"name\":\"x\",\"startDate\":\"2026-06-01\"}]}");
        assertEquals(1, holidays.size());
        assertNotNull(holidays.get(0));
    }
}
