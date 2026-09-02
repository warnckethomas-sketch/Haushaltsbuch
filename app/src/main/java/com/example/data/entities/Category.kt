package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

object CategoryType {
    const val FIXED_COST = "FIXED_COST"
    const val RECURRING_EXPENSE = "RECURRING_EXPENSE"
    const val INVOICE = "INVOICE"
    const val INCOME = "INCOME"
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String
)
