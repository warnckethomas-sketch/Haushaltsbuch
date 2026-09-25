package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import com.example.ui.components.PrintHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    monthName: String,
    year: Int,
    incomes: List<Income>,
    fixedCosts: List<FixedCost>,
    recurringExpenses: List<RecurringExpense>,
    invoices: List<Invoice>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    val mNum = monthNumber(monthName)
    val activeIncomes = incomes.filter { it.isEffectiveInMonth(year, mNum, incomes) }
    val regularIncomes = activeIncomes.filter { !it.isCareAllowanceItem }
    val pflegegeldIncomes = activeIncomes.filter { it.isCareAllowanceItem }

    val activeFixed = fixedCosts.filter { it.isEffectiveInMonth(year, mNum, fixedCosts) }
        .sortedWith(compareBy({ if (it.category.isBlank()) "Sonstiges" else it.category }, { it.title }))
    val activeRecurring = recurringExpenses.filter { it.isDueInMonth(year, mNum) }
        .sortedWith(compareBy({ !it.isActive }, { it.getNextDueMonthDelta(mNum, year) }, { it.getFirstDueMonth() }, { it.title.lowercase() }))
    val activeInvoices = invoices.filter { it.isDueInMonth(year, mNum) }

    val totalIncome = regularIncomes.sumOf { it.amount }
    val totalPflegegeld = pflegegeldIncomes.sumOf { it.amount }
    val totalFixed = activeFixed.sumOf { it.amount }
    val totalRecurring = activeRecurring.sumOf { it.getAmountForMonth(year, mNum) }
    val totalInvoices = activeInvoices.sumOf { it.amount }
    val totalExpenses = totalFixed + totalRecurring + totalInvoices
    val netBalance = totalIncome - totalExpenses

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Druckvorschau",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$monthName $year",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                PrintHelper.printMonthlyReport(
                                    context, monthName, year, incomes, fixedCosts, recurringExpenses, invoices
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("print_now_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Drucken", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF1F5F9))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Simplified A4 Sheet Preview Container
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, top = 16.dp, end = 18.dp, bottom = 22.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Header: Logo Left, Title & Subtitle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTPYfEJ3M3SS0WV5torrO4t-9V7X-4PNqvAxUWy-pHuODbDiJgRRPvcAsRS&s=10",
                                contentDescription = "Haushaltsbuch Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "HAUSHALTSBUCH",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Monatsübersicht $monthName $year",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        Text(
                            text = java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(java.util.Date()),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF3B82F6), thickness = 1.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Simplified Summary Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0F2FE).copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFBAE6FD), shape = RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("EINNAHMEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("+${fmt.format(totalIncome)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("AUSGABEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("-${fmt.format(totalExpenses)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SALDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(
                                text = fmt.format(netBalance),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netBalance >= 0) IncomeGreen else ExpenseRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. Incomes Section
                    PrintSectionTitle("1. Monatliche Einnahmen (Regulär)")
                    if (regularIncomes.isEmpty()) {
                        Text("Keine regulären Einnahmen erfasst.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        regularIncomes.forEachIndexed { index, inc ->
                            PrintTableRow(inc.title, inc.category, "+${fmt.format(inc.amount)}", IncomeGreen, isEven = index % 2 == 1)
                        }
                    }
                    PrintTableTotalRow("Gesamte Einnahmen", "+${fmt.format(totalIncome)}", IncomeGreen)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1b. Pflegegeld Section
                    PrintSectionTitle("1b. Pflegegeld¹", color = MaterialTheme.colorScheme.tertiary)
                    if (pflegegeldIncomes.isEmpty()) {
                        Text("Kein Pflegegeld für diesen Monat erfasst.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        pflegegeldIncomes.forEachIndexed { index, inc ->
                            val pct = if (inc.percentageShare % 1.0 == 0.0) inc.percentageShare.toInt().toString() else inc.percentageShare.toString()
                            val senderText = if (inc.sender.isNotBlank()) "Überweiser: ${inc.sender}" else "Pflegegeld"
                            val pgDetail = if (inc.pflegegeldBaseAmount > 0 || inc.pflegehilfsmittelAmount > 0) {
                                " [PG-Basis: ${fmt.format(inc.pflegegeldBaseAmount)}, Hilfsmittel: ${fmt.format(inc.pflegehilfsmittelAmount)}]"
                            } else ""
                            val subtitle = "$senderText ($pct% von ${fmt.format(inc.calculatedCareTotalBase)})$pgDetail"
                            PrintTableRow(inc.title, subtitle, "+${fmt.format(inc.amount)}", MaterialTheme.colorScheme.tertiary, isEven = index % 2 == 1)
                        }
                    }
                    PrintTableTotalRow("Gesamt Pflegegeld (separat)", "+${fmt.format(totalPflegegeld)}", MaterialTheme.colorScheme.tertiary)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Fixed Costs Section
                    PrintSectionTitle("2. Monatliche Fixkosten")
                    if (activeFixed.isEmpty()) {
                        Text("Keine Fixkosten erfasst.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        activeFixed.forEachIndexed { index, fc ->
                            val dueStr = if (fc.endYear != null && fc.endMonth != null) {
                                val yy = fc.endYear % 100
                                String.format(Locale.GERMANY, "%d. des Monats (bis %02d-%02d)", fc.dueDayOfMonth, fc.endMonth, yy)
                            } else {
                                "${fc.dueDayOfMonth}. des Monats"
                            }
                            PrintTableRow(fc.title, fc.category, dueStr, ExpenseRed, isEven = index % 2 == 1, showGebuchtBox = true)
                        }
                    }
                    PrintTableTotalRow("Gesamt Fixkosten", "-${fmt.format(totalFixed)}", ExpenseRed, hasGebuchtCol = true)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Recurring Expenses Section
                    PrintSectionTitle("3. Wiederkehrende Ausgaben ($monthName)")
                    if (activeRecurring.isEmpty()) {
                        Text("Keine wiederkehrenden Sonderausgaben.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        activeRecurring.forEachIndexed { index, re ->
                            val intervalStr = if (re.endYear != null && re.endMonth != null) {
                                val yy = re.endYear % 100
                                String.format(Locale.GERMANY, "%s (bis %02d-%02d)", re.getIntervalText(), re.endMonth, yy)
                            } else {
                                re.getIntervalText()
                            }
                            PrintTableRow(re.title, "${re.category} ($intervalStr)", "-${fmt.format(re.getAmountForMonth(year, mNum))}", ExpenseRed, isEven = index % 2 == 1, showGebuchtBox = true)
                        }
                    }
                    PrintTableTotalRow("Gesamt Wiederkehrend ($monthName)", "-${fmt.format(totalRecurring)}", ExpenseRed, hasGebuchtCol = true)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Invoices Section
                    PrintSectionTitle("4. Rechnungen ($monthName)")
                    if (activeInvoices.isEmpty()) {
                        Text("Keine Rechnungen fällig.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        activeInvoices.forEachIndexed { index, inv ->
                            val status = if (inv.isPaid) "Bezahlt" else "Offen"
                            PrintTableRow("${inv.title} (${inv.vendor})", "Fällig: ${inv.getDueDateFormatted()} [$status]", "-${fmt.format(inv.amount)}", ExpenseRed, isEven = index % 2 == 1, showGebuchtBox = true, isGebuchtChecked = inv.isPaid)
                        }
                    }
                    PrintTableTotalRow("Gesamt Rechnungen ($monthName)", "-${fmt.format(totalInvoices)}", ExpenseRed, hasGebuchtCol = true)

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(6.dp))

                    val netSaldo = totalIncome - totalExpenses
                    val gesamtSaldoMitPflegegeld = netSaldo + totalPflegegeld
                    Text(
                        text = "¹ Gesamtsaldo inkl. Pflegegeld: ${fmt.format(gesamtSaldoMitPflegegeld)} (Saldo: ${fmt.format(netSaldo)} + Pflegegeld: ${fmt.format(totalPflegegeld)})",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Haushaltsbuch App • DIN 5008 • Erstellt am ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(java.util.Date())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Seite 1 von 1",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1E40AF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrintSectionTitle(title: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(vertical = 5.dp)
    )
}

@Composable
private fun PrintTableRow(
    title: String,
    subtitle: String,
    amountText: String,
    amountColor: Color,
    isEven: Boolean = false,
    showGebuchtBox: Boolean = false,
    isGebuchtChecked: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) Color(0x33E0F2FE) else Color.White)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 12.5.sp, color = Color.Gray)
        }
        Text(
            amountText,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = amountColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (showGebuchtBox) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.2.dp, Color(0xFF64748B), RoundedCornerShape(3.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGebuchtChecked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Gebucht",
                            tint = IncomeGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
}

@Composable
private fun PrintTableTotalRow(
    label: String,
    totalAmountText: String,
    amountColor: Color,
    hasGebuchtCol: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x99BAE6FD))
            .border(BorderStroke(0.8.dp, Color(0xFF3B82F6)))
            .padding(vertical = 7.dp, horizontal = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
        Text(
            totalAmountText,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = amountColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Reserved space to align with table rows
        }
    }
}

private fun monthNumber(monthName: String): Int = when (monthName.lowercase(Locale.GERMANY)) {
    "januar" -> 1
    "februar" -> 2
    "märz", "maerz" -> 3
    "april" -> 4
    "mai" -> 5
    "juni" -> 6
    "juli" -> 7
    "august" -> 8
    "september" -> 9
    "oktober" -> 10
    "november" -> 11
    "dezember" -> 12
    else -> 1
}
