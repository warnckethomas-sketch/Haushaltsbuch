package com.example.data.dao

import androidx.room.*
import com.example.data.entities.Invoice
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY isPaid ASC, dueYear DESC, dueMonth DESC, dueDay DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices")
    suspend fun getAllInvoicesList(): List<Invoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice)

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Int)

    @Query("UPDATE invoices SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)
}
