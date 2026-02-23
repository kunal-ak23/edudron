package com.datagami.edudron.student.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsTemporalConversionTest {

    private final AnalyticsService analyticsService = new AnalyticsService();

    @Test
    void testConvertToOffsetDateTime() {
        // java.sql.Timestamp
        java.sql.Timestamp timestamp = new java.sql.Timestamp(System.currentTimeMillis());
        OffsetDateTime fromTimestamp = (OffsetDateTime) ReflectionTestUtils.invokeMethod(analyticsService,
                "convertToOffsetDateTime", timestamp);
        assertNotNull(fromTimestamp);
        assertEquals(timestamp.toInstant().atOffset(ZoneOffset.UTC), fromTimestamp);

        // java.time.Instant
        Instant instant = Instant.now();
        OffsetDateTime fromInstant = (OffsetDateTime) ReflectionTestUtils.invokeMethod(analyticsService,
                "convertToOffsetDateTime", instant);
        assertNotNull(fromInstant);
        assertEquals(instant.atOffset(ZoneOffset.UTC), fromInstant);

        // OffsetDateTime
        OffsetDateTime odt = OffsetDateTime.now();
        OffsetDateTime fromOdt = (OffsetDateTime) ReflectionTestUtils.invokeMethod(analyticsService,
                "convertToOffsetDateTime", odt);
        assertNotNull(fromOdt);
        assertEquals(odt, fromOdt);

        // Null
        assertNull(ReflectionTestUtils.invokeMethod(analyticsService, "convertToOffsetDateTime", (Object) null));
    }

    @Test
    void testConvertToLocalDate() {
        // java.sql.Date
        java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
        LocalDate fromSqlDate = (LocalDate) ReflectionTestUtils.invokeMethod(analyticsService, "convertToLocalDate",
                sqlDate);
        assertNotNull(fromSqlDate);
        assertEquals(sqlDate.toLocalDate(), fromSqlDate);

        // java.sql.Timestamp
        java.sql.Timestamp timestamp = new java.sql.Timestamp(System.currentTimeMillis());
        LocalDate fromTimestamp = (LocalDate) ReflectionTestUtils.invokeMethod(analyticsService, "convertToLocalDate",
                timestamp);
        assertNotNull(fromTimestamp);
        assertEquals(timestamp.toLocalDateTime().toLocalDate(), fromTimestamp);

        // java.time.LocalDate
        LocalDate localDate = LocalDate.now();
        LocalDate fromLocalDate = (LocalDate) ReflectionTestUtils.invokeMethod(analyticsService, "convertToLocalDate",
                localDate);
        assertNotNull(fromLocalDate);
        assertEquals(localDate, fromLocalDate);

        // java.time.Instant
        Instant instant = Instant.now();
        LocalDate fromInstant = (LocalDate) ReflectionTestUtils.invokeMethod(analyticsService, "convertToLocalDate",
                instant);
        assertNotNull(fromInstant);
        assertEquals(instant.atZone(ZoneOffset.UTC).toLocalDate(), fromInstant);

        // Null
        assertNull(ReflectionTestUtils.invokeMethod(analyticsService, "convertToLocalDate", (Object) null));
    }
}
