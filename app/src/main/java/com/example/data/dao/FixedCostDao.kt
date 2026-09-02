package com.example.data.dao

import androidx.room.*
import com.example.data.entities.FixedCost
import kotlinx.coroutines.flow.Flow

@Dao
interface FixedCostDao {
    @Query("SELECT * FROM fixed_costs ORDER BY isActive DESC, title ASC")
    fun getAllFixedCosts(): Flow<List<FixedCost>>

    @Query("SELECT * FROM fixed_costs")
    suspend fun getAllFixedCostsList(): List<FixedCost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixedCost(fixedCost: FixedCost)

    @Update
    suspend fun updateFixedCost(fixedCost: FixedCost)

    @Delete
    suspend fun deleteFixedCost(fixedCost: FixedCost)

    @Query("DELETE FROM fixed_costs WHERE id = :id")
    suspend fun deleteFixedCostById(id: Int)

    @Query("UPDATE fixed_costs SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)
}
