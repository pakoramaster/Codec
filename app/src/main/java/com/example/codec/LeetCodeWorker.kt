package com.yourpackage.codec

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneOffset

class LeetCodeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        return try {
            val username = inputData.getString("username") ?: return Result.failure()
            val submissions = LeetCodeApi.getTodaySubmissions(username)

            if (submissions == 0) {
                NotificationUtils.send(applicationContext)
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
