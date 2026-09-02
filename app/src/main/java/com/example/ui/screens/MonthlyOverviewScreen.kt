package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import com.example.ui.components.MonthPickerBar
import com.example.ui.components.PrintHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.WarningOrange
import java.text.NumberFormat
import java.util.Locale

import com.example.ui.components.AddEditFixedCostDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyOverviewScreen(
    selectedMonth: Int,
    selectedYear: Int,
    incomes: List<Income>,
    fixedCosts: List<FixedCost>,
    recurringExpenses: List<RecurringExpense>,
    invoices: List<Invoice>,
    categories: List<String> = emptyList(),
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthSelected: (month: Int, year: Int) -> Unit,
    onOpenPrintPreview: () -> Unit,
    onSaveFixedCost: (FixedCost) -> Unit = {},
    onDeleteFixedCost: (FixedCost) -> Unit = {},
    onManageCategories: () -> Unit = {},
    onQuickAddCategory: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val monthName = PrintHelper.getGermanMonthName(selectedMonth)
    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    var editingFixedCost by remember { mutableStateOf<FixedCost?>(null) }
    var showFixedCostDialog by remember { mutableStateOf(false) }

    val activeIncomes = incomes.filter { it.isEffectiveInMonth(selectedYear, selectedMonth, incomes) }
    val regularActiveIncomes = activeIncomes.filter { !it.isCareAllowanceItem }
    val pflegegeldActiveIncomes = activeIncomes.filter { it.isCareAllowanceItem }

    val activeFixed = fixedCosts.filter { it.isEffectiveInMonth(selectedYear, selectedMonth, fixedCosts) }
        .sortedWith(compareBy({ if (it.category.isBlank()) "Sonstiges" else it.category }, { it.title }))
    val activeRecurring = recurringExpenses.filter { it.isDueInMonth(selectedYear, selectedMonth) }
        .sortedWith(compareBy({ !it.isActive }, { it.getNextDueMonthDelta(selectedMonth, selectedYear) }, { it.getFirstDueMonth() }, { it.title.lowercase() }))
    val activeInvoices = invoices.filter { it.isDueInMonth(selectedYear, selectedMonth) }

    val totalIncome = regularActiveIncomes.sumOf { it.amount }
    val totalPflegegeld = pflegegeldActiveIncomes.sumOf { it.amount }
    val totalFixed = activeFixed.sumOf { it.amount }
    val totalRecurring = activeRecurring.sumOf { it.getAmountForMonth(selectedYear, selectedMonth) }
    val totalInvoices = activeInvoices.sumOf { it.amount }
    val totalExpenses = totalFixed + totalRecurring + totalInvoices
    val netBalance = totalIncome - totalExpenses

    var expandIncomes by remember { mutableStateOf(true) }
    var expandPflegegeld by remember { mutableStateOf(true) }
    var expandFixed by remember { mutableStateOf(true) }
    var expandRecurring by remember { mutableStateOf(true) }
    var expandInvoices by remember { mutableStateOf(true) }

    val lastSavedTime = remember(incomes, fixedCosts, recurringExpenses, invoices) {
        java.text.SimpleDateFormat("HH:mm", Locale.GERMANY).format(java.util.Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Automatisch gesichert um $lastSavedTime Uhr",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Month Picker
        MonthPickerBar(
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onMonthSelected = onMonthSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Print & Export Action Buttons Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    PrintHelper.printMonthlyReport(
                        context, monthName, selectedYear, incomes, fixedCosts, recurringExpenses, invoices
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("print_report_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Drucken / PDF")
            }

            OutlinedButton(
                onClick = onOpenPrintPreview,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_report_button")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Vorschau")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // High Density Hero Saldo Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "VERFÜGBARER SALDO",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fmt.format(netBalance),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (netBalance >= 0) IncomeGreen else ExpenseRed
                            ) {
                                Text(
                                    text = if (netBalance >= 0) "ÜBERSCHUSS" else "DEFIZIT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Einnahmen: +${fmt.format(totalIncome)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "Ausgaben: -${fmt.format(totalExpenses)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // High Density 2x2 Category Grid
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kategorien & Übersicht",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$monthName $selectedYear",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryGridCard(
                            title = "Fixkosten",
                            amount = "-${fmt.format(totalFixed)}",
                            icon = Icons.Default.Home,
                            modifier = Modifier.weight(1f)
                        )
                        CategoryGridCard(
                            title = "Wiederkehrend",
                            amount = "-${fmt.format(totalRecurring)}",
                            icon = Icons.Default.Repeat,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryGridCard(
                            title = "Rechnungen",
                            amount = "-${fmt.format(totalInvoices)}",
                            icon = Icons.Default.ReceiptLong,
                            modifier = Modifier.weight(1f)
                        )
                        CategoryGridCard(
                            title = "Einnahmen",
                            amount = "+${fmt.format(totalIncome)}",
                            icon = Icons.Default.TrendingUp,
                            iconTint = IncomeGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECTION 1: INCOMES
            item {
                OverviewExpandableSection(
                    title = "1. Einnahmen ($monthName $selectedYear)",
                    totalAmount = totalIncome,
                    isPositive = true,
                    isExpanded = expandIncomes,
                    onToggle = { expandIncomes = !expandIncomes },
                    fmt = fmt
                ) {
                    if (regularActiveIncomes.isEmpty()) {
                        Text("Keine regulären Einnahmen für diesen Monat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        regularActiveIncomes.forEach { inc ->
                            OverviewItemRow(inc.title, inc.category, "+${fmt.format(inc.amount)}", IncomeGreen)
                        }
                    }
                }
            }

            // SECTION 1b: PFLEGEGELD (SEPARAT)
            if (pflegegeldActiveIncomes.isNotEmpty() || expandPflegegeld) {
                item {
                    OverviewExpandableSection(
                        title = "1b. Pflegegeld (separat - nicht in Gesamtsumme)",
                        totalAmount = totalPflegegeld,
                        isPositive = true,
                        isExpanded = expandPflegegeld,
                        onToggle = { expandPflegegeld = !expandPflegegeld },
                        fmt = fmt,
                        customAmountColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                    ) {
                        if (pflegegeldActiveIncomes.isEmpty()) {
                            Text("Kein Pflegegeld für diesen Monat erfasst.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            pflegegeldActiveIncomes.forEach { inc ->
                                val pct = if (inc.percentageShare % 1.0 == 0.0) inc.percentageShare.toInt().toString() else inc.percentageShare.toString()
                                val senderText = if (inc.sender.isNotBlank()) "Überweiser: ${inc.sender}" else "Pflegegeld"
                                OverviewItemRow(
                                    title = inc.title,
                                    subtitle = "$senderText ($pct% Anteil von ${fmt.format(inc.calculatedCareTotalBase)})",
                                    amountText = "+${fmt.format(inc.amount)}",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: FIXED COSTS
            item {
                OverviewExpandableSection(
                    title = "2. Monatliche Fixkosten",
                    totalAmount = totalFixed,
                    isPositive = false,
                    isExpanded = expandFixed,
                    onToggle = { expandFixed = !expandFixed },
                    fmt = fmt
                ) {
                    if (activeFixed.isEmpty()) {
                        Text("Keine Fixkosten erfasst.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        activeFixed.forEach { fc ->
                            val isOverride = fc.overriddenFixedCostId != null || (fc.specificYear == selectedYear && fc.specificMonth == selectedMonth && !fc.isRecurring)
                            val endInfo = if (fc.endYear != null && fc.endMonth != null) " • bis ${PrintHelper.getGermanMonthName(fc.endMonth)} ${fc.endYear}" else ""
                            OverviewEditableItemRow(
                                title = fc.title,
                                subtitle = "${fc.category} (${fc.dueDayOfMonth}. des Monats$endInfo)",
                                amountText = "-${fmt.format(fc.amount)}",
                                color = ExpenseRed,
                                isOverride = isOverride,
                                onAdjustForMonth = {
                                    editingFixedCost = if (isOverride) {
                                        fc
                                    } else {
                                        FixedCost(
                                            id = 0,
                                            title = fc.title,
                                            amount = fc.amount,
                                            category = fc.category,
                                            dueDayOfMonth = fc.dueDayOfMonth,
                                            notes = fc.notes,
                                            isActive = true,
                                            isRecurring = false,
                                            specificYear = selectedYear,
                                            specificMonth = selectedMonth,
                                            overriddenFixedCostId = fc.id
                                        )
                                    }
                                    showFixedCostDialog = true
                                },
                                onDeleteOverride = if (isOverride) {
                                    { onDeleteFixedCost(fc) }
                                } else null
                            )
                        }
                    }
                }
            }

            // SECTION 3: RECURRING EXPENSES
            item {
                OverviewExpandableSection(
                    title = "3. Wiederkehrende Ausgaben im Monat",
                    totalAmount = totalRecurring,
                    isPositive = false,
                    isExpanded = expandRecurring,
                    onToggle = { expandRecurring = !expandRecurring },
                    fmt = fmt
                ) {
                    if (activeRecurring.isEmpty()) {
                        Text("Keine wiederkehrenden Sonderausgaben fällig.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        activeRecurring.forEach { re ->
                            OverviewItemRow(re.title, "${re.category} (${re.getIntervalText()})", "-${fmt.format(re.getAmountForMonth(selectedYear, selectedMonth))}", ExpenseRed)
                        }
                    }
                }
            }

            // SECTION 4: INVOICES
            item {
                OverviewExpandableSection(
                    title = "4. Rechnungen ($monthName)",
                    totalAmount = totalInvoices,
                    isPositive = false,
                    isExpanded = expandInvoices,
                    onToggle = { expandInvoices = !expandInvoices },
                    fmt = fmt
                ) {
                    if (activeInvoices.isEmpty()) {
                        Text("Keine Rechnungen im $monthName fällig.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        activeInvoices.forEach { inv ->
                            val statusStr = if (inv.isPaid) "Bezahlt" else "Offen"
                            OverviewItemRow(inv.title, "${inv.vendor} (Fällig ${inv.getDueDateFormatted()} - $statusStr)", "-${fmt.format(inv.amount)}", ExpenseRed)
                        }
                    }
                }
            }
        }
    }

    if (showFixedCostDialog) {
        AddEditFixedCostDialog(
            initialCost = editingFixedCost,
            defaultSelectedMonth = selectedMonth,
            defaultSelectedYear = selectedYear,
            availableCategories = categories,
            onDismiss = { showFixedCostDialog = false },
            onSave = { cost ->
                onSaveFixedCost(cost)
                showFixedCostDialog = false
            },
            onManageCategories = onManageCategories,
            onQuickAddCategory = onQuickAddCategory
        )
    }
}

@Composable
private fun OverviewEditableItemRow(
    title: String,
    subtitle: String,
    amountText: String,
    color: Color,
    isOverride: Boolean = false,
    onAdjustForMonth: () -> Unit,
    onDeleteOverride: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (isOverride) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Monatsanpassung",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(onClick = onAdjustForMonth, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Betrag für diesen Monat anpassen",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (isOverride && onDeleteOverride != null) {
                IconButton(onClick = onDeleteOverride, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Monatsanpassung entfernen",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewExpandableSection(
    title: String,
    totalAmount: Double,
    isPositive: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    fmt: NumberFormat,
    customAmountColor: Color? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (containerColor != null) CardDefaults.cardColors(containerColor = containerColor) else CardDefaults.cardColors(),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${if (isPositive) "+" else "-"}${fmt.format(totalAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = customAmountColor ?: (if (isPositive) IncomeGreen else ExpenseRed)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun OverviewItemRow(title: String, subtitle: String, amountText: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CategoryGridCard(
    title: String,
    amount: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

