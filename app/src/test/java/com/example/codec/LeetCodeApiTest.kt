package com.example.codec

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LeetCodeApiTest {

    private fun timestamp(date: String): Long = LocalDate.parse(date)
        .atStartOfDay(ZoneOffset.UTC)
        .toEpochSecond()

    private val now = Instant.parse("2026-09-20T18:00:00Z") // Sunday
    private val calendar = mapOf(
        timestamp("2026-08-31") to 50,
        timestamp("2026-09-01") to 4,
        timestamp("2026-09-13") to 20,
        timestamp("2026-09-14") to 1,
        timestamp("2026-09-18") to 2,
        timestamp("2026-09-20") to 3,
        timestamp("2026-09-21") to 100
    )

    @Test
    fun dayCountsOnlyToday() {
        assertEquals(
            3,
            LeetCodeApi.countSubmissions(calendar, LeaderboardPeriod.DAY, now)
        )
    }

    @Test
    fun weekCountsMondayThroughToday() {
        assertEquals(
            6,
            LeetCodeApi.countSubmissions(calendar, LeaderboardPeriod.WEEK, now)
        )
    }

    @Test
    fun monthCountsFirstOfMonthThroughToday() {
        assertEquals(
            30,
            LeetCodeApi.countSubmissions(calendar, LeaderboardPeriod.MONTH, now)
        )
    }
}
