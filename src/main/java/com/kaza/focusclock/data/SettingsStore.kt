package com.kaza.focusclock.data

import android.content.Context
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_clock_settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var accent: String
        get() = prefs.getString("accent", "#858BFF") ?: "#858BFF"
        set(value) = prefs.edit().putString("accent", value).apply()

    var sleepStart: Int
        get() = prefs.getInt("sleep_start", 22 * 60)
        set(value) = prefs.edit().putInt("sleep_start", value).apply()

    var wakeTime: Int
        get() = prefs.getInt("wake_time", 5 * 60)
        set(value) = prefs.edit().putInt("wake_time", value).apply()

    var schoolQuiet: Boolean
        get() = prefs.getBoolean("school_quiet", false)
        set(value) = prefs.edit().putBoolean("school_quiet", value).apply()

    var conflictShown: Boolean
        get() = prefs.getBoolean("conflict_shown", false)
        set(value) = prefs.edit().putBoolean("conflict_shown", value).apply()

    var morningStudyEnabled: Boolean
        get() = prefs.getBoolean("morning_study_enabled", false)
        set(value) = prefs.edit().putBoolean("morning_study_enabled", value).apply()

    var goalMinutes: Int
        get() = prefs.getInt("goal_minutes", 150)
        set(value) = prefs.edit().putInt("goal_minutes", value.coerceIn(15, 900)).apply()

    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("reminder_enabled", value).apply()


    fun toJson(): JSONObject = JSONObject().apply {
        put("dark_mode", darkMode)
        put("accent", accent)
        put("sleep_start", sleepStart)
        put("wake_time", wakeTime)
        put("school_quiet", schoolQuiet)
        put("conflict_shown", conflictShown)
        put("morning_study_enabled", morningStudyEnabled)
        put("goal_minutes", goalMinutes)
        put("reminder_enabled", reminderEnabled)
    }

    fun restoreFromJson(json: JSONObject) {
        prefs.edit()
            .putBoolean("dark_mode", json.optBoolean("dark_mode", true))
            .putString("accent", json.optString("accent", "#858BFF"))
            .putInt("sleep_start", json.optInt("sleep_start", 22 * 60).coerceIn(0, 1439))
            .putInt("wake_time", json.optInt("wake_time", 5 * 60).coerceIn(0, 1439))
            .putBoolean("school_quiet", json.optBoolean("school_quiet", false))
            .putBoolean("conflict_shown", json.optBoolean("conflict_shown", true))
            .putBoolean("morning_study_enabled", json.optBoolean("morning_study_enabled", false))
            .putInt("goal_minutes", json.optInt("goal_minutes", 150).coerceIn(15, 900))
            .putBoolean("reminder_enabled", json.optBoolean("reminder_enabled", true))
            .apply()
    }

    fun isQuietAt(minute: Int, dayOfWeek: Int): Boolean {
        if (insideQuietWindow(minute, sleepStart, wakeTime)) return true
        if (schoolQuiet && dayOfWeek in 1..5) {
            val inMorningSchool = minute in (6 * 60 + 55)..(if (dayOfWeek == 1) 11 * 60 + 40 else 10 * 60 + 50)
            val inAfternoonSchool = minute in (13 * 60 + 20)..(16 * 60 + 30)
            return inMorningSchool || inAfternoonSchool
        }
        return false
    }

    fun hasSleepConflict(startMinute: Int, endMinute: Int): Boolean {
        // The supported default study block does not cross midnight.
        return (startMinute until endMinute).any { insideQuietWindow(it, sleepStart, wakeTime) }
    }

    fun sleepDurationMinutes(): Int {
        return if (wakeTime >= sleepStart) wakeTime - sleepStart else (24 * 60 - sleepStart) + wakeTime
    }

    private fun insideQuietWindow(minute: Int, start: Int, end: Int): Boolean {
        return if (start == end) false else if (start < end) minute in start until end else minute >= start || minute < end
    }
}
