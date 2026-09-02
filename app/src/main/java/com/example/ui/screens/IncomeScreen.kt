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
import com.example.data.entities.Income
import com.example.ui.components.AddEditIncomeDialog
import com.example.ui.components.MonthPickerBar
import com.example.ui.components.PrintHelper
import com.example.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    incomes: List<Income>,
    categories: List<Category> = emptyList(),
    selectedMonth: Int,
    selectedYear: Int,
    settingsManager: com.example.utils.SettingsManager? = null,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthSelected: (month: Int, year: Int) -> Unit,
    onSaveIncome: (Income) -> Unit,
    onToggleActive: (Income) -> Unit,
    onDeleteIncome: (Income) -> Unit,
    onAddCategory: (String, String) -> Unit = { _, _ -> },
    onManageCategories: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingIncome by remember { mutableStateOf<Income?>(null) }
    var dialogPflegegeldMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Reguläre Einnahmen, 1 = Pflegegeld
    var showOnlyEffectiveForMonth by remember { mutableStateOf(true) }

    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    val monthName = PrintHelper.getGermanMonthName(selectedMonth)

    val incomeCategories = categories.filter { it.type == CategoryType.INCOME }.map { it.name }

    val activeIncomesForMonth = incomes.filter { it.isEffectiveInMonth(selectedYear, selectedMonth, incomes) }
    val regularActiveIncomes = activeIncomesForMonth.filter { !it.isCareAllowanceItem }
    val pflegegeldActiveIncomes = activeIncomesForMonth.filter { it.isCareAllowanceItem }

    val totalRegularIncomeAmount = regularActiveIncomes.sumOf { it.amount }
    val totalPflegegeldAmount = pflegegeldActiveIncomes.sumOf { it.amount }

    val displayedIncomes = remember(incomes, showOnlyEffectiveForMonth, selectedMonth, selectedYear) {
        if (showOnlyEffectiveForMonth) {
            incomes.filter { it.isEffectiveInMonth(selectedYear, selectedMonth, incomes) }
        } else {
            incomes
        }
    }

    val regularDisplayedIncomes = displayedIncomes.filter { !it.isCareAllowanceItem }
    val pflegegeldDisplayedIncomes = displayedIncomes.filter { it.isCareAllowanceItem }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingIncome = null
                    dialogPflegegeldMode = (selectedTab == 1)
                    showDialog = true
                },
                containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_income_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = if (selectedTab == 1) "Pflegegeld hinzufügen" else "Einnahme hinzufügen")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            MonthPickerBar(
                selectedMonth = selectedMonth,
                selectedYear = selectedYear,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onMonthSelected = onMonthSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Selection for Regular vs Pflegegeld
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Reguläre Einnahmen",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pflegegeld",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                            if (pflegegeldActiveIncomes.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                    Text("${pflegegeldActiveIncomes.size}")
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Summary Total Regular Income Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = IncomeGreen.copy(alpha = 0.12f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Monatliche Reguläre Einnahmen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${regularActiveIncomes.size} Posten für $monthName $selectedYear",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "+${fmt.format(totalRegularIncomeAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                // Summary Pflegegeld Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MedicalServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pflegegeld (separat)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "${pflegegeldActiveIncomes.size} Empfänger/Überweiser für $monthName $selectedYear",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = "+${fmt.format(totalPflegegeldAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ℹ️ Wird nicht in die reguläre monatliche Gesamteinnahme oder den Saldo eingerechnet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showOnlyEffectiveForMonth,
                    onClick = { showOnlyEffectiveForMonth = true },
                    label = { Text("Gültig in $monthName", fontSize = 12.sp) },
                    leadingIcon = if (showOnlyEffectiveForMonth) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                val currentTabTotalCount = if (selectedTab == 0) incomes.count { !it.isCareAllowanceItem } else incomes.count { it.isCareAllowanceItem }
                FilterChip(
                    selected = !showOnlyEffectiveForMonth,
                    onClick = { showOnlyEffectiveForMonth = false },
                    label = { Text("Alle ($currentTabTotalCount)", fontSize = 12.sp) },
                    leadingIcon = if (!showOnlyEffectiveForMonth) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val currentItemsToDisplay = if (selectedTab == 0) regularDisplayedIncomes else pflegegeldDisplayedIncomes

            if (currentItemsToDisplay.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Default.MedicalServices else Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 1) "Kein Pflegegeld erfasst" else "Keine regulären Einnahmen vorhanden",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedTab == 1) "Klicke auf 'Pflegegeld erfassen', um Pflegekassen oder Überweiser einzutragen." else "Erfasse Lohn, Gehalt, Kindergeld oder eine Monatsanpassung.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(currentItemsToDisplay, key = { it.id }) { item ->
                        val isEffective = item.isEffectiveInMonth(selectedYear, selectedMonth, incomes)
                        IncomeCard(
                            income = item,
                            isEffective = isEffective,
                            selectedMonth = selectedMonth,
                            selectedYear = selectedYear,
                            fmt = fmt,
                            onToggleActive = { onToggleActive(item) },
                            onEdit = {
                                editingIncome = item
                                dialogPflegegeldMode = item.isCareAllowanceItem
                                showDialog = true
                            },
                            onAdjustForMonth = { recurringIncome ->
                                editingIncome = Income(
                                    title = "${recurringIncome.title} ($monthName $selectedYear)",
                                    amount = recurringIncome.amount,
                                    category = recurringIncome.category,
                                    notes = recurringIncome.notes,
                                    isRecurring = false,
                                    specificMonth = selectedMonth,
                                    specificYear = selectedYear,
                                    overriddenIncomeId = recurringIncome.id,
                                    isPflegegeld = recurringIncome.isCareAllowanceItem,
                                    sender = recurringIncome.sender
                                )
                                dialogPflegegeldMode = recurringIncome.isCareAllowanceItem
                                showDialog = true
                            },
                            onDelete = { onDeleteIncome(item) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddEditIncomeDialog(
            initialIncome = editingIncome,
            defaultSelectedMonth = selectedMonth,
            defaultSelectedYear = selectedYear,
            availableCategories = incomeCategories,
            isPflegegeldMode = dialogPflegegeldMode,
            settingsManager = settingsManager,
            onDismiss = { showDialog = false },
            onSave = { inc ->
                onSaveIncome(inc)
                showDialog = false
            },
            onManageCategories = onManageCategories,
            onQuickAddCategory = { name ->
                onAddCategory(name, CategoryType.INCOME)
            }
        )
    }
}

@Composable
fun IncomeCard(
    income: Income,
    isEffective: Boolean,
    selectedMonth: Int,
    selectedYear: Int,
    fmt: NumberFormat,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onAdjustForMonth: (Income) -> Unit,
    onDelete: () -> Unit
) {
    val isOverride = income.overriddenIncomeId != null
    val isCare = income.isCareAllowanceItem
    val monthName = PrintHelper.getGermanMonthName(selectedMonth)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCare -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                isOverride -> IncomeGreen.copy(alpha = 0.08f)
                isEffective && income.isActive -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        border = CardDefaults.outlinedCardBorder(enabled = isOverride || isCare)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(if (isCare) "Pflegegeld" else income.category, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isCare) MaterialTheme.colorScheme.tertiaryContainer else IncomeGreen.copy(alpha = 0.15f)
                        )
                    )
                    Text(
                        text = when {
                            isOverride -> "Monatsanpassung ($monthName $selectedYear)"
                            income.isRecurring -> "Wiederkehrend"
                            else -> "Einmalig (${PrintHelper.getGermanMonthName(income.specificMonth ?: 1)} ${income.specificYear})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isOverride) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOverride) IncomeGreen else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                Switch(
                    checked = income.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = income.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (income.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (income.isCareAllowanceItem) {
                        Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                            if (income.sender.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Überweiser: ${income.sender}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                            val careBase = income.calculatedCareTotalBase
                            val pctDisplay = if (income.percentageShare % 1.0 == 0.0) income.percentageShare.toInt().toString() else income.percentageShare.toString()
                            val gradText = if (income.pflegegrad != null) "PG ${income.pflegegrad}" else "Gesamtpflege"
                            val subDetails = if (income.pflegehilfsmittelAmount > 0) "+ ${fmt.format(income.pflegehilfsmittelAmount)} Hilfsmittel" else ""
                            Text(
                                text = "$pctDisplay % Anteil von ${fmt.format(careBase)} ($gradText $subDetails)".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else if (income.sender.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Überweiser: ${income.sender}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (income.notes.isNotBlank()) {
                        Text(
                            text = income.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "+${fmt.format(income.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (!income.isActive) MaterialTheme.colorScheme.outline else if (isCare) MaterialTheme.colorScheme.tertiary else IncomeGreen,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (income.isRecurring) {
                    OutlinedButton(
                        onClick = { onAdjustForMonth(income) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Betrag für $monthName anpassen", fontSize = 11.sp)
                    }
                } else if (isOverride) {
                    Text(
                        text = "Ersetzt den Standardbetrag für $monthName",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = IncomeGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = if (isOverride) "Anpassung löschen (Standard wiederherstellen)" else "Löschen",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

