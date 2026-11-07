package com.kaza.focusclock.data

import java.time.LocalDate

data class ScheduleTask(
    val id: Long,
    val title: String,
    val subject: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val reminderMinutes: Int,
    val countsForGoal: Boolean,
    val date: LocalDate? = null,
    val status: String = "PENDING",
    val actualMinutes: Int = 0
) {
    val durationMinutes: Int get() = if (endMinute >= startMinute) endMinute - startMinute else 0
}

data class RoadmapMilestone(
    val id: Long,
    val category: String,
    val phase: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val weight: Int,
    val status: String,
    val confidence: Int,
    val notes: String
)

data class DayStats(
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val focusMinutes: Int,
    val completedTasks: Int,
    val partialTasks: Int,
    val missedTasks: Int
) {
    val percent: Int get() = if (plannedMinutes <= 0) 0 else (completedMinutes * 100 / plannedMinutes).coerceIn(0, 100)
}
