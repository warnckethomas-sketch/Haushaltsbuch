package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.Category
import com.example.data.entities.CategoryType
import com.example.data.entities.RecurringExpense
import com.example.ui.components.AddEditRecurringExpenseDialog
import com.example.ui.components.MonthPickerBar
import com.example.ui.components.PrintHelper
import com.example.ui.components.RecurrenceScheduleDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.WarningOrange
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    recurringExpenses: List<RecurringExpense>,
    categories: List<Category> = emptyList(),
    selectedMonth: Int = 1,
    selectedYear: Int = 2026,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onMonthSelected: (month: Int, year: Int) -> Unit = { _, _ -> },
    onSaveExpense: (RecurringExpense) -> Unit,
    onToggleActive: (RecurringExpense) -> Unit,
    onDeleteExpense: (RecurringExpense) -> Unit,
    onAddCategory: (String, String) -> Unit = { _, _ -> },
    onManageCategories: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var scheduleExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    val recurringCategories = categories.filter { it.type == CategoryType.RECURRING_EXPENSE }.map { it.name }

    val activeExpenses = recurringExpenses.filter { it.isActive }
    val totalAnnualAmount = activeExpenses.sumOf { exp ->
        if (exp.isSplit || exp.intervalMonths == 0) {
            if (exp.totalYearlyAmount > 0) exp.totalYearlyAmount
            else {
                val count = exp.getCustomMonthAmountMap().size
                if (count > 0) exp.amount * count else exp.amount
            }
        } else {
            exp.amount * (12.0 / exp.intervalMonths)
        }
    }

    val sortedExpenses = remember(recurringExpenses, selectedMonth, selectedYear) {
        recurringExpenses.sortedWith(
            compareBy(
                { !it.isActive },
                { it.getNextDueMonthDelta(selectedMonth, selectedYear) },
                { it.getFirstDueMonth() },
                { it.title.lowercase() }
            )
        )
    }

    val groupedExpenses = remember(sortedExpenses) {
        sortedExpenses.groupBy {
            if (it.category.isBlank()) "Sonstiges" else it.category
        }.toSortedMap()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingExpense = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_recurring_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Wiederkehrende Kosten hinzufügen")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Summary Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Wiederkehrende Ausgaben",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeExpenses.size} von ${recurringExpenses.size} Positionen aktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "-${fmt.format(totalAnnualAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "pro Jahr (gesamt)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (recurringExpenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keine wiederkehrenden Kosten erfasst",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Erfasse Verträge mit vierteljährlicher, halbjährlicher oder jährlicher Fälligkeit (z.B. GEZ, KFZ-Steuer).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    groupedExpenses.forEach { (category, categoryExpenses) ->
                        item(key = "header_$category") {
                            val activeCatExpenses = categoryExpenses.filter { it.isActive }
                            val totalCatAnnual = activeCatExpenses.sumOf { exp ->
                                if (exp.isSplit || exp.intervalMonths == 0) {
                                    if (exp.totalYearlyAmount > 0) exp.totalYearlyAmount
                                    else {
                                        val count = exp.getCustomMonthAmountMap().size
                                        if (count > 0) exp.amount * count else exp.amount
                                    }
                                } else {
                                    exp.amount * (12.0 / exp.intervalMonths)
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${categoryExpenses.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Text(
                                        text = "-${fmt.format(totalCatAnnual)} / Jahr",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        items(categoryExpenses, key = { it.id }) { item ->
                            RecurringExpenseCard(
                                expense = item,
                                fmt = fmt,
                                onToggleActive = { onToggleActive(item) },
                                onShowSchedule = { scheduleExpense = item },
                                onEdit = {
                                    editingExpense = item
                                    showDialog = true
                                },
                                onDelete = { onDeleteExpense(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    scheduleExpense?.let { exp ->
        RecurrenceScheduleDialog(
            expense = exp,
            onDismiss = { scheduleExpense = null }
        )
    }

    if (showDialog) {
        AddEditRecurringExpenseDialog(
            initialExpense = editingExpense,
            availableCategories = recurringCategories,
            onDismiss = { showDialog = false },
            onSave = { expense ->
                onSaveExpense(expense)
                showDialog = false
            },
            onManageCategories = onManageCategories,
            onQuickAddCategory = { name ->
                onAddCategory(name, CategoryType.RECURRING_EXPENSE)
            }
        )
    }
}

@Composable
fun RecurringExpenseCard(
    expense: RecurringExpense,
    fmt: NumberFormat,
    onToggleActive: () -> Unit,
    onShowSchedule: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onShowSchedule,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Wiederholungsplan anzeigen",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = expense.getIntervalText(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.testTag("recurrence_schedule_chip_${expense.id}")
                )

                Switch(
                    checked = expense.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (expense.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val runtimeInfo = if (expense.endYear != null && expense.endMonth != null) {
                        "Start: ${PrintHelper.getGermanMonthName(expense.startMonth)} ${expense.startYear} | Ende: ${PrintHelper.getGermanMonthName(expense.endMonth)} ${expense.endYear}"
                    } else {
                        "Start: ${PrintHelper.getGermanMonthName(expense.startMonth)} ${expense.startYear}"
                    }

                    Text(
                        text = runtimeInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (expense.notes.isNotBlank()) {
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${fmt.format(expense.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (expense.isActive) ExpenseRed else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (expense.isSplit || expense.intervalMonths == 0) {
                        Text(
                            text = "pro Fälligkeit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                        if (expense.totalYearlyAmount > 0) {
                            Text(
                                text = "(${fmt.format(expense.totalYearlyAmount)} gesamt/Jahr)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

