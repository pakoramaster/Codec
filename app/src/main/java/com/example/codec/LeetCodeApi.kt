package com.example.codec

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset

object LeetCodeApi {

    fun getTodaySubmissions(username: String): Int {
        val client = OkHttpClient()
        val gson = Gson()

        val queryJson = """
        {
          "query": "query userProfileCalendar(${'$'}username: String!) { matchedUser(username: ${'$'}username) { submissionCalendar } }",
          "variables": { "username": "$username" }
        }
        """.trimIndent()

        val body = queryJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://leetcode.com/graphql")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return 0
            }

            val responseBody = response.body?.string() ?: return 0

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val data = json.getAsJsonObject("data") ?: return 0
            val matchedUser = data.get("matchedUser")
            if (matchedUser == null || matchedUser.isJsonNull) {
                return 0
            }

            val calendarStr = matchedUser
                .asJsonObject["submissionCalendar"]
                .asString

            val calendarMap: Map<String, Double> =
                gson.fromJson(calendarStr, Map::class.java) as Map<String, Double>

            val todayKey = getTodayUnixDay()
            return calendarMap[todayKey]?.toInt() ?: 0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTodayUnixDay(): String {
        val now = Instant.now()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()

        return now.toString()
    }
}
