package com.example.data.dao

import androidx.room.*
import com.example.data.entities.Income
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY isActive DESC, title ASC")
    fun getAllIncomes(): Flow<List<Income>>

    @Query("SELECT * FROM incomes")
    suspend fun getAllIncomesList(): List<Income>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: Income)

    @Update
    suspend fun updateIncome(income: Income)

    @Delete
    suspend fun deleteIncome(income: Income)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: Int)

    @Query("UPDATE incomes SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)
}
