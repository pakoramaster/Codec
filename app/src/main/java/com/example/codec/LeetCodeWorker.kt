package com.example.codec

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class LeetCodeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        return try {
            val username = inputData.getString("username") ?: return Result.failure()
            val dailyGoal = inputData.getInt("daily_goal", UserSettings.DEFAULT_DAILY_GOAL)
                .coerceAtLeast(1)
            val submissions = LeetCodeApi.getTodaySubmissions(username)

            if (submissions < dailyGoal) {
                NotificationUtils.send(applicationContext, submissions, dailyGoal)
            }
            Log.d("LeetCodeWorker", "Daily submissions = $submissions/$dailyGoal")

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

}
