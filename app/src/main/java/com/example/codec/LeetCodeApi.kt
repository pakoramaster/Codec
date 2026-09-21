package com.example.codec

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset

object LeetCodeApi {

    private val client = OkHttpClient()
    private val gson = Gson()

    @RequiresApi(Build.VERSION_CODES.O)
    fun isValidUser(username: String): Boolean {
        return fetchSubmissionCalendar(username) != null
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun getTodaySubmissions(username: String): Int {
        return getSubmissionCount(username, LeaderboardPeriod.DAY)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getSubmissionCount(
        username: String,
        period: LeaderboardPeriod,
        now: Instant = Instant.now()
    ): Int {
        val calendar = fetchSubmissionCalendar(username) ?: return 0
        return countSubmissions(calendar, period, now)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun countSubmissions(
        calendar: Map<Long, Int>,
        period: LeaderboardPeriod,
        now: Instant,
        zoneId: ZoneId = ZoneOffset.UTC
    ): Int {
        val today = now.atZone(zoneId).toLocalDate()
        val firstDay = when (period) {
            LeaderboardPeriod.DAY -> today
            LeaderboardPeriod.WEEK -> today.minusDays(
                (today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
            )
            LeaderboardPeriod.MONTH -> today.withDayOfMonth(1)
        }

        return calendar.entries.sumOf { (timestamp, count) ->
            val date = Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDate()
            if (!date.isBefore(firstDay) && !date.isAfter(today)) count else 0
        }
    }

    private fun fetchSubmissionCalendar(username: String): Map<Long, Int>? {
        if (username.isBlank()) return null

        val queryJson = gson.toJson(
            mapOf(
                "query" to "query userProfileCalendar(\$username: String!) { matchedUser(username: \$username) { submissionCalendar } }",
                "variables" to mapOf("username" to username.trim())
            )
        )
        val request = Request.Builder()
            .url("https://leetcode.com/graphql")
            .post(queryJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val matchedUser = json.getAsJsonObject("data")?.get("matchedUser")
            if (matchedUser == null || matchedUser.isJsonNull) return null
            val calendarString = matchedUser.asJsonObject["submissionCalendar"]?.asString
                ?: return emptyMap()
            return JsonParser.parseString(calendarString)
                .asJsonObject
                .entrySet()
                .mapNotNull { (timestamp, count) ->
                    timestamp.toLongOrNull()?.let { it to count.asInt }
            }.toMap()
        }
    }
}
