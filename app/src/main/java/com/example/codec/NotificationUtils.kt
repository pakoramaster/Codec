package com.example.codec

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    private const val CHANNEL_ID = "leetcode_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LeetCode Reminder",
                NotificationManager.IMPORTANCE_HIGH
            )

            val manager =
                context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun send(context: Context) {
        val originalIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_stat_name)

        // 1. Define the desired visual scale (e.g., 75%)
        val scale = 0.60f
        val scaledWidth = (originalIcon.width * scale).toInt()
        val scaledHeight = (originalIcon.height * scale).toInt()

        // 2. Create a smaller version of the icon
        val scaledIcon = Bitmap.createScaledBitmap(originalIcon, scaledWidth, scaledHeight, true)

        // 3. Create a new, full-sized, transparent canvas
        val paddedBitmap = Bitmap.createBitmap(originalIcon.width, originalIcon.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)

        // 4. Calculate the position to draw the small icon in the center
        val left = (originalIcon.width - scaledWidth) / 2f
        val top = (originalIcon.height - scaledHeight) / 2f

        // 5. Draw the scaled icon onto the center of the transparent canvas
        canvas.drawBitmap(scaledIcon, left, top, null)

        // 6. Tint the new "padded" bitmap red
        val coloredBitmap = paddedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint()
        val filter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        paint.colorFilter = filter
        val tintCanvas = Canvas(coloredBitmap)
        tintCanvas.drawBitmap(coloredBitmap, 0f, 0f, paint)

        // List of possible notification texts
        val notificationTexts = listOf(
            "The Job Market has changed.",
            "I\'ve never done LeetCode for anyone but myself.",
            "He who controls LeetCode, controls the online assessment.",
            "It\'s easy to forget an algorithm in the middle of an assessment.",
            "It\'s only when I\'m doing LeetCode at midnight. The only time I feel truly alive.",
            "A referral means nothing in this job economy.",
            "A strong man doesn\'t need to read the future. He grinds LeetCode."
        )

        // Randomly select a notification text from the list
        val notificationText = notificationTexts.random()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name) // Correct monochrome icon
            .setLargeIcon(coloredBitmap) // Padded & tinted icon
            .setColor(Color.RED)
            .setContentTitle("Snake")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify((0..10000).random(), notification)
    }
}
