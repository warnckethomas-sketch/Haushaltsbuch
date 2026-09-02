package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val invoiceNumber: String = "",
    val title: String,
    val vendor: String = "",
    val amount: Double,
    val dueYear: Int,
    val dueMonth: Int, // 1-12
    val dueDay: Int, // 1-31
    val isPaid: Boolean = false,
    val paidYear: Int? = null,
    val paidMonth: Int? = null,
    val paidDay: Int? = null,
    val category: String,
    val notes: String = ""
) {
    fun isDueInMonth(targetYear: Int, targetMonth: Int): Boolean {
        return dueYear == targetYear && dueMonth == targetMonth
    }

    fun getDueDateFormatted(): String {
        return "%02d.%02d.%04d".format(dueDay, dueMonth, dueYear)
    }

    fun getPaidDateFormatted(): String? {
        if (!isPaid || paidYear == null || paidMonth == null || paidDay == null) return null
        return "%02d.%02d.%04d".format(paidDay, paidMonth, paidYear)
    }
}
