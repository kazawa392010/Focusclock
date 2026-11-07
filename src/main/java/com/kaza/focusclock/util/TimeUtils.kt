package com.kaza.focusclock.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

    fun formatMinute(minute: Int): String = LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59)).format(timeFormatter)
    fun parseMinute(value: String): Int? = runCatching {
        val p = value.trim().split(":")
        require(p.size == 2)
        val h = p[0].toInt()
        val m = p[1].toInt()
        require(h in 0..23 && m in 0..59)
        h * 60 + m
    }.getOrNull()
    fun formatDate(date: LocalDate): String = date.format(dateFormatter)
    fun dayMask(dayOfWeek: Int): Int = 1 shl (dayOfWeek - 1)
    fun dayName(dayOfWeek: Int): String = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật").getOrElse(dayOfWeek - 1) { "" }
}
