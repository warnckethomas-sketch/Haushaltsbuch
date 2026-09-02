package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fixed_costs")
data class FixedCost(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val dueDayOfMonth: Int = 1,
    val notes: String = "",
    val isActive: Boolean = true,
    val isRecurring: Boolean = true, // monatlich wiederkehrend by default
    val endYear: Int? = null, // null means unbefristet / dauerhaft
    val endMonth: Int? = null, // 1-12
    val specificYear: Int? = null,
    val specificMonth: Int? = null, // 1-12 if single time or month override
    val overriddenFixedCostId: Int? = null // ID of recurring FixedCost if this is a month-specific override
) {
    fun isEffectiveInMonth(targetYear: Int, targetMonth: Int, allFixedCosts: List<FixedCost> = emptyList()): Boolean {
        if (!isActive) return false

        // Check expiration / end date (Abbuchung bis einschließlich)
        if (endYear != null && endMonth != null) {
            val totalTargetMonths = targetYear * 12 + (targetMonth - 1)
            val totalEndMonths = endYear * 12 + (endMonth - 1)
            if (totalTargetMonths > totalEndMonths) return false
        }

        if (isRecurring) {
            // Check if there is an active month-specific override for this recurring fixed cost
            val hasOverride = allFixedCosts.any {
                it.overriddenFixedCostId == this.id &&
                it.specificYear == targetYear &&
                it.specificMonth == targetMonth &&
                it.isActive
            }
            return !hasOverride
        }

        return specificYear == targetYear && specificMonth == targetMonth
    }
}

fun List<FixedCost>.getEffectiveFixedCostsForMonth(targetYear: Int, targetMonth: Int): List<FixedCost> {
    return this.filter { it.isEffectiveInMonth(targetYear, targetMonth, this) }
}
