package com.example.data.repository

import com.example.data.dao.CategoryDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.IncomeDao
import com.example.data.dao.InvoiceDao
import com.example.data.dao.RecurringExpenseDao
import com.example.data.entities.Category
import com.example.data.entities.CategoryType
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class FinanceRepository(
    private val fixedCostDao: FixedCostDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val invoiceDao: InvoiceDao,
    private val incomeDao: IncomeDao,
    private val categoryDao: CategoryDao
) {
    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    fun getCategoriesByType(type: String): Flow<List<Category>> = categoryDao.getCategoriesByType(type)

    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)

    suspend fun updateCategory(oldCategory: Category, newName: String) {
        val updatedCategory = oldCategory.copy(name = newName)
        categoryDao.updateCategory(updatedCategory)
        
        // Propagate category name change to existing entries
        when (oldCategory.type) {
            CategoryType.FIXED_COST -> fixedCostDao.updateCategoryName(oldCategory.name, newName)
            CategoryType.RECURRING_EXPENSE -> recurringExpenseDao.updateCategoryName(oldCategory.name, newName)
            CategoryType.INVOICE -> invoiceDao.updateCategoryName(oldCategory.name, newName)
            CategoryType.INCOME -> incomeDao.updateCategoryName(oldCategory.name, newName)
        }
    }

    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    // Fixed Costs
    val fixedCosts: Flow<List<FixedCost>> = fixedCostDao.getAllFixedCosts()
    suspend fun insertFixedCost(cost: FixedCost) = fixedCostDao.insertFixedCost(cost)
    suspend fun updateFixedCost(cost: FixedCost) = fixedCostDao.updateFixedCost(cost)
    suspend fun deleteFixedCost(cost: FixedCost) = fixedCostDao.deleteFixedCost(cost)

    // Recurring Expenses
    val recurringExpenses: Flow<List<RecurringExpense>> = recurringExpenseDao.getAllRecurringExpenses()
    suspend fun insertRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.insertRecurringExpense(expense)
    suspend fun updateRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.updateRecurringExpense(expense)
    suspend fun deleteRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.deleteRecurringExpense(expense)

    // Invoices
    val invoices: Flow<List<Invoice>> = invoiceDao.getAllInvoices()
    suspend fun insertInvoice(invoice: Invoice) = invoiceDao.insertInvoice(invoice)
    suspend fun updateInvoice(invoice: Invoice) = invoiceDao.updateInvoice(invoice)
    suspend fun deleteInvoice(invoice: Invoice) = invoiceDao.deleteInvoice(invoice)

    // Incomes
    val incomes: Flow<List<Income>> = incomeDao.getAllIncomes()
    suspend fun insertIncome(income: Income) = incomeDao.insertIncome(income)
    suspend fun updateIncome(income: Income) = incomeDao.updateIncome(income)
    suspend fun deleteIncome(income: Income) = incomeDao.deleteIncome(income)

    suspend fun restoreFromBackup(data: com.example.utils.BackupHelper.ParsedBackupData): Int {
        var addedCount = 0

        val existingCategories = categoryDao.getAllCategoriesList().toMutableList()
        val existingFixedCosts = fixedCostDao.getAllFixedCostsList().toMutableList()
        val existingRecurringExpenses = recurringExpenseDao.getAllRecurringExpensesList().toMutableList()
        val existingInvoices = invoiceDao.getAllInvoicesList().toMutableList()
        val existingIncomes = incomeDao.getAllIncomesList().toMutableList()

        // Restore Categories without duplicates
        data.categories.forEach { c ->
            val isDuplicate = existingCategories.any { existing ->
                existing.name.trim().equals(c.name.trim(), ignoreCase = true) &&
                existing.type == c.type
            }
            if (!isDuplicate) {
                val newCat = c.copy(id = 0, name = c.name.trim())
                insertCategory(newCat)
                existingCategories.add(newCat)
                addedCount++
            }
        }

        // Restore Fixed Costs without duplicates
        data.fixedCosts.forEach { f ->
            val isDuplicate = existingFixedCosts.any { existing ->
                existing.title.trim().equals(f.title.trim(), ignoreCase = true) &&
                abs(existing.amount - f.amount) < 0.001 &&
                existing.category == f.category &&
                existing.dueDayOfMonth == f.dueDayOfMonth &&
                existing.isRecurring == f.isRecurring &&
                existing.specificYear == f.specificYear &&
                existing.specificMonth == f.specificMonth
            }
            if (!isDuplicate) {
                val newFC = f.copy(id = 0, title = f.title.trim())
                insertFixedCost(newFC)
                existingFixedCosts.add(newFC)
                addedCount++
            }
        }

        // Restore Recurring Expenses without duplicates
        data.recurringExpenses.forEach { r ->
            val isDuplicate = existingRecurringExpenses.any { existing ->
                existing.title.trim().equals(r.title.trim(), ignoreCase = true) &&
                abs(existing.amount - r.amount) < 0.001 &&
                existing.category == r.category &&
                existing.intervalMonths == r.intervalMonths &&
                existing.startMonth == r.startMonth &&
                existing.startYear == r.startYear &&
                existing.endMonth == r.endMonth &&
                existing.endYear == r.endYear
            }
            if (!isDuplicate) {
                val newRE = r.copy(id = 0, title = r.title.trim())
                insertRecurringExpense(newRE)
                existingRecurringExpenses.add(newRE)
                addedCount++
            }
        }

        // Restore Invoices without duplicates
        data.invoices.forEach { inv ->
            val isDuplicate = existingInvoices.any { existing ->
                existing.title.trim().equals(inv.title.trim(), ignoreCase = true) &&
                abs(existing.amount - inv.amount) < 0.001 &&
                existing.category == inv.category &&
                existing.dueYear == inv.dueYear &&
                existing.dueMonth == inv.dueMonth &&
                existing.dueDay == inv.dueDay
            }
            if (!isDuplicate) {
                val newInv = inv.copy(id = 0, title = inv.title.trim())
                insertInvoice(newInv)
                existingInvoices.add(newInv)
                addedCount++
            }
        }

        // Restore Incomes without duplicates
        data.incomes.forEach { inc ->
            val isDuplicate = existingIncomes.any { existing ->
                existing.title.trim().equals(inc.title.trim(), ignoreCase = true) &&
                abs(existing.amount - inc.amount) < 0.001 &&
                existing.category == inc.category &&
                existing.isRecurring == inc.isRecurring &&
                existing.specificYear == inc.specificYear &&
                existing.specificMonth == inc.specificMonth &&
                existing.isPflegegeld == inc.isPflegegeld &&
                existing.sender.trim().equals(inc.sender.trim(), ignoreCase = true)
            }
            if (!isDuplicate) {
                val newInc = inc.copy(id = 0, title = inc.title.trim())
                insertIncome(newInc)
                existingIncomes.add(newInc)
                addedCount++
            }
        }

        return addedCount
    }
}
