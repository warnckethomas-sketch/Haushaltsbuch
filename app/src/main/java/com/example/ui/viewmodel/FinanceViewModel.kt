package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entities.Category
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import com.example.data.repository.FinanceRepository
import com.example.utils.BackupHelper
import com.example.utils.SettingsManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(FlowPreview::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    val settingsManager = SettingsManager(application)

    val calendar = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(calendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedMonth = MutableStateFlow(calendar.get(Calendar.MONTH) + 1) // 1-12
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    val fixedCosts: StateFlow<List<FixedCost>>
    val recurringExpenses: StateFlow<List<RecurringExpense>>
    val invoices: StateFlow<List<Invoice>>
    val incomes: StateFlow<List<Income>>
    val categories: StateFlow<List<Category>>

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(
            database.fixedCostDao(),
            database.recurringExpenseDao(),
            database.invoiceDao(),
            database.incomeDao(),
            database.categoryDao()
        )

        fixedCosts = repository.fixedCosts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        recurringExpenses = repository.recurringExpenses
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        invoices = repository.invoices
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        incomes = repository.incomes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        categories = repository.allCategories
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Automatic persistent backup trigger on any state update
        viewModelScope.launch {
            combine(fixedCosts, recurringExpenses, invoices, incomes, categories) { f, r, inv, inc, c ->
                BackupHelper.createBackupJson(c, f, r, inv, inc)
            }.debounce(600).collect { json ->
                if (json.isNotBlank() && (fixedCosts.value.isNotEmpty() || incomes.value.isNotEmpty() || invoices.value.isNotEmpty())) {
                    settingsManager.performAutoBackupIfEnabled(json)
                }
            }
        }
    }

    // --- Category Actions ---
    fun addCategory(name: String, type: String) = viewModelScope.launch {
        if (name.isNotBlank()) {
            repository.insertCategory(Category(name = name.trim(), type = type))
        }
    }

    fun updateCategory(oldCategory: Category, newName: String) = viewModelScope.launch {
        if (newName.isNotBlank() && oldCategory.name != newName.trim()) {
            repository.updateCategory(oldCategory, newName.trim())
        }
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        repository.deleteCategory(category)
    }

    fun setSelectedMonthAndYear(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
    }

    fun navigateMonth(delta: Int) {
        var m = _selectedMonth.value + delta
        var y = _selectedYear.value
        while (m > 12) {
            m -= 12
            y += 1
        }
        while (m < 1) {
            m += 12
            y -= 1
        }
        _selectedMonth.value = m
        _selectedYear.value = y
    }

    // --- Actions for Fixed Costs ---
    fun saveFixedCost(cost: FixedCost) = viewModelScope.launch {
        if (cost.id == 0) repository.insertFixedCost(cost)
        else repository.updateFixedCost(cost)
    }

    fun toggleFixedCostActive(cost: FixedCost) = viewModelScope.launch {
        repository.updateFixedCost(cost.copy(isActive = !cost.isActive))
    }

    fun deleteFixedCost(cost: FixedCost) = viewModelScope.launch {
        repository.deleteFixedCost(cost)
    }

    // --- Actions for Recurring Expenses ---
    fun saveRecurringExpense(expense: RecurringExpense) = viewModelScope.launch {
        if (expense.id == 0) repository.insertRecurringExpense(expense)
        else repository.updateRecurringExpense(expense)
    }

    fun toggleRecurringExpenseActive(expense: RecurringExpense) = viewModelScope.launch {
        repository.updateRecurringExpense(expense.copy(isActive = !expense.isActive))
    }

    fun deleteRecurringExpense(expense: RecurringExpense) = viewModelScope.launch {
        repository.deleteRecurringExpense(expense)
    }

    // --- Actions for Invoices ---
    fun saveInvoice(invoice: Invoice) = viewModelScope.launch {
        if (invoice.id == 0) repository.insertInvoice(invoice)
        else repository.updateInvoice(invoice)
    }

    fun toggleInvoicePaid(invoice: Invoice) = viewModelScope.launch {
        val now = Calendar.getInstance()
        val isNowPaid = !invoice.isPaid
        val updated = invoice.copy(
            isPaid = isNowPaid,
            paidYear = if (isNowPaid) now.get(Calendar.YEAR) else null,
            paidMonth = if (isNowPaid) now.get(Calendar.MONTH) + 1 else null,
            paidDay = if (isNowPaid) now.get(Calendar.DAY_OF_MONTH) else null
        )
        repository.updateInvoice(updated)
    }

    fun deleteInvoice(invoice: Invoice) = viewModelScope.launch {
        repository.deleteInvoice(invoice)
    }

    // --- Actions for Incomes ---
    fun saveIncome(income: Income) = viewModelScope.launch {
        if (income.id == 0) repository.insertIncome(income)
        else repository.updateIncome(income)
    }

    fun toggleIncomeActive(income: Income) = viewModelScope.launch {
        repository.updateIncome(income.copy(isActive = !income.isActive))
    }

    fun deleteIncome(income: Income) = viewModelScope.launch {
        repository.deleteIncome(income)
    }

    // --- Backup & Restore ---
    fun generateBackupJson(): String {
        return com.example.utils.BackupHelper.createBackupJson(
            categories = categories.value,
            fixedCosts = fixedCosts.value,
            recurringExpenses = recurringExpenses.value,
            invoices = invoices.value,
            incomes = incomes.value
        )
    }

    fun restoreBackup(jsonString: String, onComplete: (Boolean, String?) -> Unit) = viewModelScope.launch {
        try {
            val parsed = com.example.utils.BackupHelper.parseBackupJson(jsonString)
            val addedCount = repository.restoreFromBackup(parsed)
            val msg = if (addedCount == 0) {
                "Keine neuen Einträge importiert (alle Einträge bereits vorhanden)."
            } else {
                "$addedCount neue Einträge erfolgreich importiert (Duplikate übersprungen)."
            }
            onComplete(true, msg)
        } catch (e: Exception) {
            onComplete(false, e.localizedMessage ?: "Fehler beim Importieren der Datei")
        }
    }
}
