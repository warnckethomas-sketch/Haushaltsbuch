package com.example.data.dao

import androidx.room.*
import com.example.data.entities.RecurringExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY isActive DESC, title ASC")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAllRecurringExpensesList(): List<RecurringExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpense(expense: RecurringExpense)

    @Update
    suspend fun updateRecurringExpense(expense: RecurringExpense)

    @Delete
    suspend fun deleteRecurringExpense(expense: RecurringExpense)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpenseById(id: Int)

    @Query("UPDATE recurring_expenses SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)
}
