package com.example.codec

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneOffset
import android.util.Log

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
            Log.d("MainScreen", "Submissions count = $submissions")

            Result.success()
        } catch (e: Exception) {
            Result.retry()
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
