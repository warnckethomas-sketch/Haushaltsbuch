package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val intervalMonths: Int = 3, // 1 = Monatlich, 3 = Vierteljährlich, 6 = Halbjährlich, 12 = Jährlich, 0 = Gesplittet / Spezifische Monate
    val startYear: Int = 2026,
    val startMonth: Int = 1, // 1-12
    val endYear: Int? = null, // null means unbefristet
    val endMonth: Int? = null, // 1-12
    val dueDayOfMonth: Int = 1,
    val category: String,
    val notes: String = "",
    val isActive: Boolean = true,
    val isSplit: Boolean = false,
    val customMonths: String = "", // e.g. "3:50.0,9:50.0" or "3,9"
    val totalYearlyAmount: Double = 0.0
) {
    fun getCustomMonthAmountMap(): Map<Int, Double> {
        if (customMonths.isBlank()) return emptyMap()
        val map = mutableMapOf<Int, Double>()
        customMonths.split(",").forEach { entry ->
            val parts = entry.trim().split(":")
            val m = parts.getOrNull(0)?.toIntOrNull()
            if (m != null && m in 1..12) {
                val amt = parts.getOrNull(1)?.toDoubleOrNull() ?: amount
                map[m] = amt
            }
        }
        return map
    }

    fun isDueInMonth(targetYear: Int, targetMonth: Int): Boolean {
        if (!isActive) return false
        val totalStartMonths = startYear * 12 + (startMonth - 1)
        val totalTargetMonths = targetYear * 12 + (targetMonth - 1)
        if (totalTargetMonths < totalStartMonths) return false

        if (endYear != null && endMonth != null) {
            val totalEndMonths = endYear * 12 + (endMonth - 1)
            if (totalTargetMonths > totalEndMonths) return false
        }

        if (isSplit || customMonths.isNotBlank() || intervalMonths == 0) {
            val monthMap = getCustomMonthAmountMap()
            return monthMap.containsKey(targetMonth)
        }

        val diff = totalTargetMonths - totalStartMonths
        return diff % intervalMonths == 0
    }

    fun getAmountForMonth(targetYear: Int, targetMonth: Int): Double {
        if (!isDueInMonth(targetYear, targetMonth)) return 0.0
        if (isSplit || customMonths.isNotBlank() || intervalMonths == 0) {
            val monthMap = getCustomMonthAmountMap()
            return monthMap[targetMonth] ?: amount
        }
        return amount
    }

    fun getNextDueMonthDelta(targetMonth: Int, targetYear: Int): Int {
        if (!isActive) return 999
        for (delta in 0..11) {
            val checkM = (targetMonth - 1 + delta) % 12 + 1
            val checkY = targetYear + (targetMonth - 1 + delta) / 12
            if (isDueInMonth(checkY, checkM)) return delta
        }
        return 999
    }

    fun getFirstDueMonth(): Int {
        if (isSplit || customMonths.isNotBlank() || intervalMonths == 0) {
            val keys = getCustomMonthAmountMap().keys
            if (keys.isNotEmpty()) return keys.minOrNull() ?: 1
        }
        return startMonth
    }

    fun getIntervalText(): String = when {
        isSplit || customMonths.isNotBlank() || intervalMonths == 0 -> {
            val monthMap = getCustomMonthAmountMap()
            val monthNames = monthMap.keys.sorted().map { getShortGermanMonthName(it) }
            if (monthNames.isEmpty()) "Gesplittet"
            else "Gesplittet (${monthNames.joinToString(", ")})"
        }
        intervalMonths == 1 -> "Monatlich"
        intervalMonths == 2 -> "Alle 2 Monate"
        intervalMonths == 3 -> "Vierteljährlich (alle 3 Monate)"
        intervalMonths == 6 -> "Halbjährlich (alle 6 Monate)"
        intervalMonths == 12 -> "Jährlich (alle 12 Monate)"
        else -> "Alle $intervalMonths Monate"
    }

    private fun getShortGermanMonthName(m: Int): String = when (m) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mär"
        4 -> "Apr"
        5 -> "Mai"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Okt"
        11 -> "Nov"
        12 -> "Dez"
        else -> ""
    }
}
