package com.example.codec

import android.content.Context
import androidx.core.content.edit

data class UserSettings(
    val username: String = "",
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val reminderMinutes: Long = DEFAULT_REMINDER_MINUTES,
    val friends: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 1
        const val DEFAULT_REMINDER_MINUTES = 240L
        val ALLOWED_REMINDER_MINUTES = listOf(240L, 480L, 720L, 1440L)
    }
}

object SettingsStore {
    private const val PREFERENCES_NAME = "leetcode_prefs"
    private const val USERNAME = "username"
    private const val DAILY_GOAL = "daily_goal"
    private const val REMINDER_MINUTES = "reminder_minutes"
    private const val FRIENDS = "friends"

    fun load(context: Context): UserSettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return UserSettings(
            username = preferences.getString(USERNAME, "").orEmpty(),
            dailyGoal = preferences.getInt(DAILY_GOAL, UserSettings.DEFAULT_DAILY_GOAL)
                .coerceIn(1, 99),
            reminderMinutes = preferences.getLong(
                REMINDER_MINUTES,
                UserSettings.DEFAULT_REMINDER_MINUTES
            ).takeIf { it in UserSettings.ALLOWED_REMINDER_MINUTES }
                ?: UserSettings.DEFAULT_REMINDER_MINUTES,
            friends = preferences.getStringSet(FRIENDS, emptySet())
                .orEmpty()
                .filter(String::isNotBlank)
                .distinctBy { it.lowercase() }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        )
    }

    fun save(context: Context, settings: UserSettings) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putString(USERNAME, settings.username.trim())
            putInt(DAILY_GOAL, settings.dailyGoal.coerceIn(1, 99))
            putLong(
                REMINDER_MINUTES,
                settings.reminderMinutes.takeIf { it in UserSettings.ALLOWED_REMINDER_MINUTES }
                    ?: UserSettings.DEFAULT_REMINDER_MINUTES
            )
            putStringSet(
                FRIENDS,
                settings.friends
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinctBy { it.lowercase() }
                    .toSet()
            )
        }
    }
}

enum class LeaderboardPeriod(val label: String) {
    DAY("DAY"),
    WEEK("WEEK"),
    MONTH("MONTH")
}

data class LeaderboardEntry(
    val username: String,
    val submissions: Int,
    val isCurrentUser: Boolean = false,
    val isPending: Boolean = false
)
