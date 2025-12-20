package com.yourpackage.codec

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneOffset

class LeetCodeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val todayKey = getTodayUnixDay()
            val submissions = LeetCodeApi.getTodaySubmissions()

            val prefs = applicationContext
                .getSharedPreferences("leetcode_prefs", Context.MODE_PRIVATE)

            val lastNotified = prefs.getString("last_notified", "")

            if (submissions == 0 && lastNotified != todayKey) {
                NotificationUtils.send(applicationContext)
                prefs.edit().putString("last_notified", todayKey).apply()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun getTodayUnixDay(): String {
        val now = Instant.now()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()

        return now.toString()
    }
}
