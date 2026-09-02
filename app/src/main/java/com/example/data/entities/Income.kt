package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val isRecurring: Boolean = true, // monatlich wiederkehrend
    val specificYear: Int? = null,
    val specificMonth: Int? = null, // 1-12 if single time or month override
    val category: String,
    val notes: String = "",
    val isActive: Boolean = true,
    val overriddenIncomeId: Int? = null, // ID of recurring Income if this is a month-specific override
    val isPflegegeld: Boolean = false, // Separate type/flag for Pflegegeld
    val sender: String = "", // Überweiser / Pflegekasse / Zahler
    val pflegegrad: Int? = null, // Pflegegrad (1-5)
    val pflegegeldBaseAmount: Double = 0.0, // Basisbetrag aus Pflegegrad
    val pflegehilfsmittelAmount: Double = 0.0, // Pflegehilfsmittel (z.B. 42 €)
    val percentageShare: Double = 100.0 // Prozentualer Anteil des Überweisers (z.B. 100%, 70%, 50%)
) {
    val isCareAllowanceItem: Boolean
        get() = isPflegegeld || category.equals("Pflegegeld", ignoreCase = true)

    val calculatedCareTotalBase: Double
        get() = if (pflegegeldBaseAmount > 0.0 || pflegehilfsmittelAmount > 0.0) {
            pflegegeldBaseAmount + pflegehilfsmittelAmount
        } else if (percentageShare > 0.0) {
            amount / (percentageShare / 100.0)
        } else {
            amount
        }

    fun isEffectiveInMonth(targetYear: Int, targetMonth: Int, allIncomes: List<Income> = emptyList()): Boolean {
        if (!isActive) return false

        if (isRecurring) {
            // Check if there is a month-specific override active for this recurring item
            val hasOverride = allIncomes.any {
                it.overriddenIncomeId == this.id &&
                it.specificYear == targetYear &&
                it.specificMonth == targetMonth &&
                it.isActive
            }
            return !hasOverride
        }

        return specificYear == targetYear && specificMonth == targetMonth
    }
}

fun List<Income>.getEffectiveIncomesForMonth(targetYear: Int, targetMonth: Int): List<Income> {
    return this.filter { it.isEffectiveInMonth(targetYear, targetMonth, this) }
}
