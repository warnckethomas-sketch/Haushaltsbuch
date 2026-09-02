package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.RecurringExpense
import com.example.ui.theme.ExpenseRed
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceScheduleDialog(
    expense: RecurringExpense,
    onDismiss: () -> Unit
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    val currentCal = Calendar.getInstance()
    val currentYear = currentCal.get(Calendar.YEAR)
    val currentMonth = currentCal.get(Calendar.MONTH) + 1

    // State for chosen time horizon (in years from current year, e.g., 1, 2, 3, 5 years)
    var selectedYearsAhead by remember { mutableIntStateOf(2) }
    var viewMode by remember { mutableStateOf(0) } // 0 = Liste der Termine, 1 = Kalenderübersicht

    val targetYear = currentYear + selectedYearsAhead

    // Calculate all occurrences from start up to targetYear
    val occurrences = remember(expense, targetYear, selectedYearsAhead) {
        val list = mutableListOf<RecurrenceOccurrence>()
        val startY = expense.startYear
        val startM = expense.startMonth

        for (y in startY..targetYear) {
            val endM = if (y == targetYear) 12 else 12
            for (m in 1..endM) {
                if (expense.isDueInMonth(y, m)) {
                    val isPast = y < currentYear || (y == currentYear && m < currentMonth)
                    val isCurrent = y == currentYear && m == currentMonth
                    val monthName = PrintHelper.getGermanMonthName(m)
                    val dayFormatted = String.format(Locale.GERMANY, "%02d", expense.dueDayOfMonth)
                    list.add(
                        RecurrenceOccurrence(
                            year = y,
                            month = m,
                            day = expense.dueDayOfMonth,
                            dateFormatted = "$dayFormatted. $monthName $y",
                            monthName = monthName,
                            isPast = isPast,
                            isCurrentMonth = isCurrent
                        )
                    )
                }
            }
        }
        list
    }

    val futureOccurrences = occurrences.filter { !it.isPast }
    val totalFutureAmount = futureOccurrences.sumOf { expense.getAmountForMonth(it.year, it.month) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("recurrence_schedule_dialog"),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wiederholungsplan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = expense.getIntervalText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Horizon selector
                Text(
                    text = "Vorschau-Zeitraum bis Ende:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1 to "+1 Jahr", 2 to "+2 Jahre", 3 to "+3 Jahre", 5 to "+5 Jahre").forEach { (years, label) ->
                        FilterChip(
                            selected = selectedYearsAhead == years,
                            onClick = { selectedYearsAhead = years },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // KPI Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${futureOccurrences.size} zukünftige Fälligkeiten",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fällig am ${expense.dueDayOfMonth}. des jeweiligen Monats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "-${fmt.format(totalFutureAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                            Text(
                                text = "Gesamtsumme",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // View mode toggle
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewMode == 0,
                        onClick = { viewMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Terminliste", fontSize = 12.sp)
                        }
                    }
                    SegmentedButton(
                        selected = viewMode == 1,
                        onClick = { viewMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kalenderübersicht", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (viewMode == 0) {
                    // List view of dates
                    if (occurrences.isEmpty()) {
                        Text(
                            text = "Keine Termine im ausgewählten Zeitraum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(occurrences) { occ ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            occ.isCurrentMonth -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            occ.isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = if (occ.isCurrentMonth) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (occ.isPast) Icons.Default.CheckCircle else if (occ.isCurrentMonth) Icons.Default.EventAvailable else Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = if (occ.isPast) Color.Gray else if (occ.isCurrentMonth) MaterialTheme.colorScheme.primary else ExpenseRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = occ.dateFormatted,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (occ.isCurrentMonth || !occ.isPast) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (occ.isPast) Color.Gray else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (occ.isCurrentMonth) {
                                                    Text(
                                                        text = "Diesen Monat fällig",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "-${fmt.format(expense.getAmountForMonth(occ.year, occ.month))}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (occ.isPast) Color.Gray else ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Calendar Yearly Grid View
                    val displayYears = (currentYear..targetYear).toList()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(displayYears) { y ->
                            Column {
                                Text(
                                    text = "Jahr $y",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                ) {
                                    items((1..12).toList()) { m ->
                                        val isDue = expense.isDueInMonth(y, m)
                                        val isPast = y < currentYear || (y == currentYear && m < currentMonth)
                                        val isCurrent = y == currentYear && m == currentMonth
                                        val mName = PrintHelper.getGermanMonthName(m).take(3)

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        isDue && isCurrent -> MaterialTheme.colorScheme.primary
                                                        isDue && !isPast -> ExpenseRed.copy(alpha = 0.85f)
                                                        isDue && isPast -> Color.Gray.copy(alpha = 0.4f)
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isCurrent) 1.5.dp else 0.5.dp,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = mName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isDue) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isDue) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (isDue) {
                                                    Text(
                                                        text = "${expense.dueDayOfMonth}.",
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.9f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_schedule_dialog")
            ) {
                Text("Schließen")
            }
        }
    )
}

data class RecurrenceOccurrence(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateFormatted: String,
    val monthName: String,
    val isPast: Boolean,
    val isCurrentMonth: Boolean
)
