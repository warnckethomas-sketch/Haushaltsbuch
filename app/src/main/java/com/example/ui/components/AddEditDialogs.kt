package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.CarePayer
import com.example.data.entities.CategoryType
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import com.example.data.entities.serializeCarePayers
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// --- REUSABLE CATEGORY SELECTOR DROPDOWN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdownSelector(
    selectedCategory: String,
    availableCategories: List<String>,
    defaultCategories: List<String>,
    onCategorySelected: (String) -> Unit,
    onQuickAddCategory: (String) -> Unit,
    onManageCategories: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }

    val allCategoriesList = remember(availableCategories, defaultCategories, selectedCategory) {
        val merged = (availableCategories.ifEmpty { defaultCategories } + selectedCategory).filter { it.isNotBlank() }.distinct()
        merged
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kategorie", style = MaterialTheme.typography.labelLarge)
            TextButton(
                onClick = onManageCategories,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kategorien bearbeiten", fontSize = 12.sp)
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allCategoriesList.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            onCategorySelected(cat)
                            expanded = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "+ Neue Kategorie anlegen",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        showQuickAddDialog = true
                    }
                )
            }
        }
    }

    if (showQuickAddDialog) {
        var newCatName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Neue Kategorie anlegen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = {
                            newCatName = it
                            isError = false
                        },
                        label = { Text("Kategoriename *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Text("Bitte einen Namen eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isBlank()) {
                            isError = true
                        } else {
                            val trimmed = newCatName.trim()
                            onQuickAddCategory(trimmed)
                            onCategorySelected(trimmed)
                            showQuickAddDialog = false
                        }
                    }
                ) {
                    Text("Anlegen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

// --- 1. FIXED COST DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditFixedCostDialog(
    initialCost: FixedCost?,
    defaultSelectedMonth: Int? = null,
    defaultSelectedYear: Int? = null,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (FixedCost) -> Unit,
    onManageCategories: () -> Unit = {},
    onQuickAddCategory: (String) -> Unit = {}
) {
    val cal = Calendar.getInstance()
    val defaultCategories = listOf("Wohnen", "Energie", "Kommunikation", "Abonnement", "Gesundheit & Sport", "Kredit/Darlehen", "Sonstiges")
    var title by remember { mutableStateOf(initialCost?.title ?: "") }
    var amountText by remember { mutableStateOf(initialCost?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var category by remember { mutableStateOf(initialCost?.category ?: availableCategories.firstOrNull() ?: defaultCategories.first()) }
    var dueDayText by remember { mutableStateOf(initialCost?.dueDayOfMonth?.toString() ?: "1") }
    var hasEndDate by remember { mutableStateOf(initialCost?.endYear != null && initialCost?.endMonth != null) }
    var endYearText by remember { mutableStateOf((initialCost?.endYear ?: (cal.get(Calendar.YEAR) + 1)).toString()) }
    var endMonth by remember { mutableIntStateOf(initialCost?.endMonth ?: (cal.get(Calendar.MONTH) + 1)) }
    var notes by remember { mutableStateOf(initialCost?.notes ?: "") }
    var isActive by remember { mutableStateOf(initialCost?.isActive ?: true) }

    val isOverride = initialCost?.overriddenFixedCostId != null
    val dialogTitle = when {
        isOverride -> "Monatsanpassung bearbeiten"
        initialCost == null -> "Neue Fixkosten erfassen"
        else -> "Fixkosten bearbeiten"
    }

    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isOverride) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Gültig für den spezifischen Monat (Standard-Wert bleibt für andere Monate erhalten).",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text("Bezeichnung / Betreff *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.'); showError = false },
                    label = { Text("Betrag (€) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                CategoryDropdownSelector(
                    selectedCategory = category,
                    availableCategories = availableCategories,
                    defaultCategories = defaultCategories,
                    onCategorySelected = { category = it },
                    onQuickAddCategory = onQuickAddCategory,
                    onManageCategories = onManageCategories
                )

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Fälligkeitstag im Monat (1 - 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasEndDate,
                        onCheckedChange = { hasEndDate = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abbuchung bis einschließlich (befristet)", fontSize = 14.sp)
                }

                if (hasEndDate) {
                    Text("Endet im Monat / Jahr", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expandedEndMonth by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedEndMonth,
                            onExpandedChange = { expandedEndMonth = !expandedEndMonth },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = PrintHelper.getGermanMonthName(endMonth),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("End-Monat") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEndMonth) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedEndMonth,
                                onDismissRequest = { expandedEndMonth = false }
                            ) {
                                (1..12).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(PrintHelper.getGermanMonthName(m)) },
                                        onClick = {
                                            endMonth = m
                                            expandedEndMonth = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = endYearText,
                            onValueChange = { endYearText = it },
                            label = { Text("End-Jahr") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktiv in Abbuchungen einberechnen")
                }

                if (showError) {
                    Text("Bitte Bezeichnung und einen gültigen Betrag eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedAmount = amountText.toDoubleOrNull()
                val parsedDay = dueDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                val parsedEndYear = if (hasEndDate) (endYearText.toIntOrNull() ?: cal.get(Calendar.YEAR)) else null
                val parsedEndMonth = if (hasEndDate) endMonth else null

                if (title.isBlank() || parsedAmount == null || parsedAmount <= 0) {
                    showError = true
                } else {
                    val cost = (initialCost ?: FixedCost(
                        title = "",
                        amount = 0.0,
                        category = "",
                        isRecurring = true,
                        specificYear = defaultSelectedYear,
                        specificMonth = defaultSelectedMonth
                    )).copy(
                        title = title.trim(),
                        amount = parsedAmount,
                        category = category,
                        dueDayOfMonth = parsedDay,
                        endYear = parsedEndYear,
                        endMonth = parsedEndMonth,
                        notes = notes.trim(),
                        isActive = isActive
                    )
                    onSave(cost)
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// --- 2. RECURRING EXPENSE DIALOG ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRecurringExpenseDialog(
    initialExpense: RecurringExpense?,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit,
    onManageCategories: () -> Unit = {},
    onQuickAddCategory: (String) -> Unit = {}
) {
    val cal = Calendar.getInstance()
    val defaultCategories = listOf("Versicherungen", "Abgaben", "Fahrzeug", "Haushalt", "Mitgliedschaft", "Sonstiges")
    var title by remember { mutableStateOf(initialExpense?.title ?: "") }
    var amountText by remember { mutableStateOf(initialExpense?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var isSplit by remember { mutableStateOf(initialExpense?.isSplit ?: (initialExpense?.intervalMonths == 0)) }
    var intervalMonths by remember { mutableIntStateOf(initialExpense?.intervalMonths ?: 3) }

    var totalYearlyAmountText by remember {
        mutableStateOf(
            when {
                initialExpense?.totalYearlyAmount != null && initialExpense.totalYearlyAmount > 0 -> initialExpense.totalYearlyAmount.toString()
                initialExpense?.isSplit == true -> {
                    val map = initialExpense.getCustomMonthAmountMap()
                    if (map.isNotEmpty()) map.values.sum().toString() else ""
                }
                else -> ""
            }
        )
    }

    val initialSelectedMonths = remember(initialExpense) {
        if (initialExpense != null) {
            val map = initialExpense.getCustomMonthAmountMap()
            if (map.isNotEmpty()) map.keys.toSet() else setOf(3, 9)
        } else {
            setOf(3, 9)
        }
    }
    var selectedMonths by remember { mutableStateOf(initialSelectedMonths) }

    var startYearText by remember { mutableStateOf((initialExpense?.startYear ?: cal.get(Calendar.YEAR)).toString()) }
    var startMonth by remember { mutableIntStateOf(initialExpense?.startMonth ?: (cal.get(Calendar.MONTH) + 1)) }
    var hasEndDate by remember { mutableStateOf(initialExpense?.endYear != null && initialExpense?.endMonth != null) }
    var endYearText by remember { mutableStateOf((initialExpense?.endYear ?: (cal.get(Calendar.YEAR) + 1)).toString()) }
    var endMonth by remember { mutableIntStateOf(initialExpense?.endMonth ?: (cal.get(Calendar.MONTH) + 1)) }
    var category by remember { mutableStateOf(initialExpense?.category ?: availableCategories.firstOrNull() ?: defaultCategories.first()) }
    var notes by remember { mutableStateOf(initialExpense?.notes ?: "") }
    var isActive by remember { mutableStateOf(initialExpense?.isActive ?: true) }

    var showError by remember { mutableStateOf(false) }

    val intervals = listOf(
        1 to "Monatlich",
        2 to "Alle 2 Monate",
        3 to "Vierteljährlich (alle 3 Monate)",
        6 to "Halbjährlich (alle 6 Monate)",
        12 to "Jährlich (alle 12 Monate)",
        0 to "Gesplittet / Spezifische Monate (z.B. Hundesteuer, Gas)"
    )

    val fmt = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.GERMANY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialExpense == null) "Wiederkehrende Ausgabe erfassen" else "Wiederkehrende Ausgabe bearbeiten", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text("Bezeichnung / Betreff *") },
                    placeholder = { Text("z.B. Hundesteuer, Gas, Haftpflicht") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Intervall / Rhythmus", style = MaterialTheme.typography.labelLarge)
                var expandedInterval by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedInterval,
                    onExpandedChange = { expandedInterval = !expandedInterval },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (isSplit || intervalMonths == 0) "Gesplittet / Spezifische Monate (z.B. Hundesteuer, Gas)" else (intervals.firstOrNull { it.first == intervalMonths }?.second ?: "Alle $intervalMonths Monate"),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInterval) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedInterval,
                        onDismissRequest = { expandedInterval = false }
                    ) {
                        intervals.forEach { (m, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    intervalMonths = m
                                    isSplit = (m == 0)
                                    expandedInterval = false
                                }
                            )
                        }
                    }
                }

                if (isSplit || intervalMonths == 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Aufteilung & Fälligkeitsmonate",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = totalYearlyAmountText,
                                onValueChange = { input ->
                                    totalYearlyAmountText = input.replace(',', '.')
                                    showError = false
                                    val tot = totalYearlyAmountText.toDoubleOrNull()
                                    if (tot != null && selectedMonths.isNotEmpty()) {
                                        amountText = String.format(java.util.Locale.US, "%.2f", tot / selectedMonths.size)
                                    }
                                },
                                label = { Text("Gesamtbetrag im Jahr (€) *") },
                                placeholder = { Text("z.B. 100.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Fälligkeitsmonate auswählen:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..12).forEach { m ->
                                    val isSelected = selectedMonths.contains(m)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedMonths = if (isSelected) {
                                                if (selectedMonths.size > 1) selectedMonths - m else selectedMonths
                                            } else {
                                                selectedMonths + m
                                            }
                                            val tot = totalYearlyAmountText.toDoubleOrNull()
                                            if (tot != null && selectedMonths.isNotEmpty()) {
                                                amountText = String.format(java.util.Locale.US, "%.2f", tot / selectedMonths.size)
                                            }
                                        },
                                        label = { Text(PrintHelper.getGermanMonthName(m).take(3), fontSize = 11.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    amountText = input.replace(',', '.')
                                    showError = false
                                    val perMonth = amountText.toDoubleOrNull()
                                    if (perMonth != null && selectedMonths.isNotEmpty()) {
                                        totalYearlyAmountText = String.format(java.util.Locale.US, "%.2f", perMonth * selectedMonths.size)
                                    }
                                },
                                label = { Text("Betrag pro Fälligkeit (€) *") },
                                placeholder = { Text("z.B. 50.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val totVal = totalYearlyAmountText.toDoubleOrNull() ?: 0.0
                            val perMonthVal = amountText.toDoubleOrNull() ?: 0.0
                            val monthNamesList = selectedMonths.sorted().map { PrintHelper.getGermanMonthName(it) }

                            if (selectedMonths.isNotEmpty() && (totVal > 0 || perMonthVal > 0)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "Aufteilungs-Übersicht:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Gesamtsumme ${fmt.format(totVal)} gesplittet auf ${selectedMonths.size} Fälligkeiten (${monthNamesList.joinToString(", ")}).",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "→ Jeweils ${fmt.format(perMonthVal)} im Fälligkeitsmonat.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.replace(',', '.'); showError = false },
                        label = { Text("Betrag pro Fälligkeit (€) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Erstmalige Fälligkeit (Start)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var expandedStartMonth by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedStartMonth,
                        onExpandedChange = { expandedStartMonth = !expandedStartMonth },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = PrintHelper.getGermanMonthName(startMonth),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Monat") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStartMonth) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStartMonth,
                            onDismissRequest = { expandedStartMonth = false }
                        ) {
                            (1..12).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(PrintHelper.getGermanMonthName(m)) },
                                    onClick = {
                                        startMonth = m
                                        expandedStartMonth = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = startYearText,
                        onValueChange = { startYearText = it },
                        label = { Text("Jahr") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasEndDate,
                        onCheckedChange = { hasEndDate = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enddatum festlegen (befristet)", fontSize = 14.sp)
                }

                if (hasEndDate) {
                    Text("Letzte Fälligkeit (Ende / Bis wann)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expandedEndMonth by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedEndMonth,
                            onExpandedChange = { expandedEndMonth = !expandedEndMonth },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = PrintHelper.getGermanMonthName(endMonth),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("End-Monat") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEndMonth) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedEndMonth,
                                onDismissRequest = { expandedEndMonth = false }
                            ) {
                                (1..12).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(PrintHelper.getGermanMonthName(m)) },
                                        onClick = {
                                            endMonth = m
                                            expandedEndMonth = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = endYearText,
                            onValueChange = { endYearText = it },
                            label = { Text("End-Jahr") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                CategoryDropdownSelector(
                    selectedCategory = category,
                    availableCategories = availableCategories,
                    defaultCategories = defaultCategories,
                    onCategorySelected = { category = it },
                    onQuickAddCategory = onQuickAddCategory,
                    onManageCategories = onManageCategories
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktiv")
                }

                if (showError) {
                    Text("Bitte Bezeichnung und einen gültigen Betrag eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedAmount = amountText.toDoubleOrNull()
                val parsedTotalYearly = totalYearlyAmountText.toDoubleOrNull() ?: 0.0
                val parsedYear = startYearText.toIntOrNull() ?: cal.get(Calendar.YEAR)
                val parsedEndYear = if (hasEndDate) (endYearText.toIntOrNull() ?: parsedYear) else null
                val parsedEndMonth = if (hasEndDate) endMonth else null

                if (title.isBlank() || parsedAmount == null || parsedAmount <= 0) {
                    showError = true
                } else {
                    val customMonthsString = if (isSplit || intervalMonths == 0) {
                        selectedMonths.sorted().joinToString(",") { m -> "$m:$parsedAmount" }
                    } else ""

                    val exp = (initialExpense ?: RecurringExpense(title = "", amount = 0.0, category = "")).copy(
                        title = title.trim(),
                        amount = parsedAmount,
                        intervalMonths = if (isSplit) 0 else intervalMonths,
                        startYear = parsedYear,
                        startMonth = startMonth,
                        endYear = parsedEndYear,
                        endMonth = parsedEndMonth,
                        category = category,
                        notes = notes.trim(),
                        isActive = isActive,
                        isSplit = isSplit,
                        customMonths = customMonthsString,
                        totalYearlyAmount = if (isSplit) (if (parsedTotalYearly > 0) parsedTotalYearly else (parsedAmount * selectedMonths.size)) else 0.0
                    )
                    onSave(exp)
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// --- 3. INVOICE DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInvoiceDialog(
    initialInvoice: Invoice?,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Invoice) -> Unit,
    onManageCategories: () -> Unit = {},
    onQuickAddCategory: (String) -> Unit = {}
) {
    val cal = Calendar.getInstance()
    val defaultCategories = listOf("Gesundheit", "Fahrzeug", "Haushalt", "Handwerker", "Reparatur", "Einkauf", "Dienstleistung", "Sonstiges")
    var invoiceNumber by remember { mutableStateOf(initialInvoice?.invoiceNumber ?: "") }
    var title by remember { mutableStateOf(initialInvoice?.title ?: "") }
    var vendor by remember { mutableStateOf(initialInvoice?.vendor ?: "") }
    var amountText by remember { mutableStateOf(initialInvoice?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    
    var dueDayText by remember { mutableStateOf((initialInvoice?.dueDay ?: cal.get(Calendar.DAY_OF_MONTH)).toString()) }
    var dueMonth by remember { mutableIntStateOf(initialInvoice?.dueMonth ?: (cal.get(Calendar.MONTH) + 1)) }
    var dueYearText by remember { mutableStateOf((initialInvoice?.dueYear ?: cal.get(Calendar.YEAR)).toString()) }

    var isPaid by remember { mutableStateOf(initialInvoice?.isPaid ?: false) }
    var category by remember { mutableStateOf(initialInvoice?.category ?: availableCategories.firstOrNull() ?: defaultCategories.first()) }
    var notes by remember { mutableStateOf(initialInvoice?.notes ?: "") }

    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialInvoice == null) "Neue Rechnung erfassen" else "Rechnung bearbeiten", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text("Betreff / Leistung *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vendor,
                        onValueChange = { vendor = it },
                        label = { Text("Rechnungssteller / Firma") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text("Rechnungs-Nr.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.'); showError = false },
                    label = { Text("Rechnungsbetrag (€) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Fälligkeitsdatum", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = { dueDayText = it },
                        label = { Text("Tag") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )

                    var expandedMonth by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedMonth,
                        onExpandedChange = { expandedMonth = !expandedMonth },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = PrintHelper.getGermanMonthName(dueMonth),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Monat") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMonth,
                            onDismissRequest = { expandedMonth = false }
                        ) {
                            (1..12).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(PrintHelper.getGermanMonthName(m)) },
                                    onClick = {
                                        dueMonth = m
                                        expandedMonth = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dueYearText,
                        onValueChange = { dueYearText = it },
                        label = { Text("Jahr") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                CategoryDropdownSelector(
                    selectedCategory = category,
                    availableCategories = availableCategories,
                    defaultCategories = defaultCategories,
                    onCategorySelected = { category = it },
                    onQuickAddCategory = onQuickAddCategory,
                    onManageCategories = onManageCategories
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isPaid, onCheckedChange = { isPaid = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPaid) "Rechnung bereits bezahlt" else "Rechnung noch offen")
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text("Bitte gültige Angaben machen.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedAmount = amountText.toDoubleOrNull()
                val parsedDay = dueDayText.toIntOrNull()?.coerceIn(1, 31) ?: cal.get(Calendar.DAY_OF_MONTH)
                val parsedYear = dueYearText.toIntOrNull() ?: cal.get(Calendar.YEAR)

                if (title.isBlank() || parsedAmount == null || parsedAmount <= 0) {
                    showError = true
                } else {
                    val inv = (initialInvoice ?: Invoice(title = "", amount = 0.0, dueYear = parsedYear, dueMonth = dueMonth, dueDay = parsedDay, category = "")).copy(
                        invoiceNumber = invoiceNumber.trim(),
                        title = title.trim(),
                        vendor = vendor.trim(),
                        amount = parsedAmount,
                        dueYear = parsedYear,
                        dueMonth = dueMonth,
                        dueDay = parsedDay,
                        isPaid = isPaid,
                        paidYear = if (isPaid) (initialInvoice?.paidYear ?: cal.get(Calendar.YEAR)) else null,
                        paidMonth = if (isPaid) (initialInvoice?.paidMonth ?: (cal.get(Calendar.MONTH) + 1)) else null,
                        paidDay = if (isPaid) (initialInvoice?.paidDay ?: cal.get(Calendar.DAY_OF_MONTH)) else null,
                        category = category,
                        notes = notes.trim()
                    )
                    onSave(inv)
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

data class CarePayerInput(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val percentageText: String = "100"
)

// --- 4. INCOME DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIncomeDialog(
    initialIncome: Income?,
    defaultSelectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    defaultSelectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    availableCategories: List<String> = emptyList(),
    isPflegegeldMode: Boolean = false,
    settingsManager: com.example.utils.SettingsManager? = null,
    onDismiss: () -> Unit,
    onSave: (Income) -> Unit,
    onManageCategories: () -> Unit = {},
    onQuickAddCategory: (String) -> Unit = {}
) {
    val cal = Calendar.getInstance()
    val defaultCategories = listOf("Gehalt", "Pflegegeld", "Staatlich", "Nebeneinkunft", "Kapital", "Rückerstattung", "Geschenk", "Einmalig", "Sonstiges")
    
    val defaultPg3 = settingsManager?.getPflegegradAmount(3) ?: 572.0
    val defaultHilfsmittel = settingsManager?.getHilfsmittelAmount() ?: 42.0

    var isPflegegeld by remember { mutableStateOf(initialIncome?.isCareAllowanceItem ?: isPflegegeldMode) }
    var title by remember { mutableStateOf(initialIncome?.title ?: "") }
    
    // Pflegegeld specific states
    var selectedPflegegrad by remember { mutableStateOf<Int?>(initialIncome?.pflegegrad ?: if (isPflegegeld) 3 else null) }
    var pflegegeldBaseText by remember { 
        mutableStateOf(
            if (initialIncome?.pflegegeldBaseAmount != null && initialIncome.pflegegeldBaseAmount > 0) 
                initialIncome.pflegegeldBaseAmount.toString() 
            else if (isPflegegeld) defaultPg3.toString() else ""
        ) 
    }
    var pflegehilfsmittelText by remember { 
        mutableStateOf(
            if (initialIncome?.pflegehilfsmittelAmount != null)
                initialIncome.pflegehilfsmittelAmount.toString()
            else if (isPflegegeld) defaultHilfsmittel.toString() else "0.0"
        ) 
    }

    // Care Payers (Wer zahlt das Pflegegeld - mehrere Zahler mit Anteilen und Summen)
    val initialPayers = remember(initialIncome) {
        val existing = initialIncome?.getCarePayers() ?: emptyList()
        if (existing.isNotEmpty()) {
            existing.map { p ->
                val pStr = if (p.percentage % 1.0 == 0.0) p.percentage.toInt().toString() else p.percentage.toString()
                CarePayerInput(name = p.name, percentageText = pStr)
            }
        } else {
            val defaultSender = initialIncome?.sender ?: ""
            val defaultPct = if (initialIncome != null) {
                if (initialIncome.percentageShare % 1.0 == 0.0) initialIncome.percentageShare.toInt().toString() else initialIncome.percentageShare.toString()
            } else "100"
            listOf(CarePayerInput(name = defaultSender, percentageText = defaultPct))
        }
    }
    var carePayers by remember { mutableStateOf(initialPayers) }

    var amountText by remember { mutableStateOf(initialIncome?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var isRecurring by remember { mutableStateOf(initialIncome?.isRecurring ?: (initialIncome?.overriddenIncomeId == null)) }
    
    var specificMonth by remember { mutableIntStateOf(initialIncome?.specificMonth ?: defaultSelectedMonth) }
    var specificYearText by remember { mutableStateOf((initialIncome?.specificYear ?: defaultSelectedYear).toString()) }

    var category by remember { mutableStateOf(initialIncome?.category ?: if (isPflegegeld) "Pflegegeld" else (availableCategories.firstOrNull { it != "Pflegegeld" } ?: defaultCategories.first())) }
    var notes by remember { mutableStateOf(initialIncome?.notes ?: "") }
    var isActive by remember { mutableStateOf(initialIncome?.isActive ?: true) }

    var showError by remember { mutableStateOf(false) }

    val isOverride = initialIncome?.overriddenIncomeId != null

    // Computed values for Pflegegeld
    val baseVal = pflegegeldBaseText.toDoubleOrNull() ?: 0.0
    val aidsVal = pflegehilfsmittelText.toDoubleOrNull() ?: 0.0
    val totalCareBase = baseVal + aidsVal
    val totalPayerPercentages = carePayers.sumOf { it.percentageText.toDoubleOrNull() ?: 0.0 }
    val totalCarePayout = carePayers.sumOf { totalCareBase * ((it.percentageText.toDoubleOrNull() ?: 0.0) / 100.0) }

    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isOverride -> "Monats-Anpassung bearbeiten"
                    initialIncome == null -> if (isPflegegeld) "Neues Pflegegeld erfassen" else "Neue Einnahme / Gehalt erfassen"
                    isPflegegeld -> "Pflegegeld bearbeiten"
                    else -> "Einnahme bearbeiten"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isOverride && initialIncome == null) {
                    Text("Art des Eintrags", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !isPflegegeld, onClick = { 
                                isPflegegeld = false 
                                if (category == "Pflegegeld") category = "Gehalt"
                            })
                            Text("Reguläre Einnahme (Gehalt, Kindergeld...)", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isPflegegeld, onClick = { 
                                isPflegegeld = true 
                                category = "Pflegegeld"
                                if (pflegegeldBaseText.isBlank()) pflegegeldBaseText = defaultPg3.toString()
                                if (pflegehilfsmittelText.isBlank()) pflegehilfsmittelText = defaultHilfsmittel.toString()
                                if (selectedPflegegrad == null) selectedPflegegrad = 3
                            })
                            Text("Pflegegeld (separatgeführt)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isPflegegeld) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Hinweis: Pflegegeld wird separat erfasst und nicht in die regulären Gesamteinnahmen eingerechnet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (isOverride) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Gilt speziell für ${PrintHelper.getGermanMonthName(specificMonth)} $specificYearText",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text(if (isPflegegeld) "Begünstigter / Bezeichnung *" else "Einnahmequelle / Betreff *") },
                    placeholder = { Text(if (isPflegegeld) "z.B. Pflegegeld PG 3 (Mutter)" else "z.B. Hauptgehalt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPflegegeld) {
                    // 1. Einstufung & monatliche Erfassung
                    Divider()
                    Text("1. Einstufung & monatliche Erfassung", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                    Text("Pflegegrad auswählen:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            1 to (settingsManager?.getPflegegradAmount(1) ?: 0.0),
                            2 to (settingsManager?.getPflegegradAmount(2) ?: 332.0),
                            3 to (settingsManager?.getPflegegradAmount(3) ?: 572.0),
                            4 to (settingsManager?.getPflegegradAmount(4) ?: 765.0),
                            5 to (settingsManager?.getPflegegradAmount(5) ?: 947.0)
                        ).forEach { (grad, standardBetrag) ->
                            FilterChip(
                                selected = selectedPflegegrad == grad,
                                onClick = {
                                    selectedPflegegrad = grad
                                    pflegegeldBaseText = standardBetrag.toString()
                                },
                                label = { Text("PG $grad", fontSize = 12.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pflegegeldBaseText,
                            onValueChange = { 
                                pflegegeldBaseText = it.replace(',', '.')
                                selectedPflegegrad = null 
                            },
                            label = { Text("Pflegegeldhöhe (€) *") },
                            placeholder = { Text("572.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = pflegehilfsmittelText,
                            onValueChange = { pflegehilfsmittelText = it.replace(',', '.') },
                            label = { Text("Hilfsmittel Betrag (€)") },
                            placeholder = { Text("42.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Card showing total care base sum
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gesamtsumme Pflege:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(fmt.format(totalCareBase), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Pflegegeldhöhe (${fmt.format(baseVal)}) + Hilfsmittel (${fmt.format(aidsVal)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Unterbereich: Wer zahlt das Pflegegeld (Zahler mit Pluszeichen)
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. Wer zahlt das Pflegegeld", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Zahler & prozentuale Aufteilung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        FilledTonalButton(
                            onClick = {
                                val currentSum = carePayers.sumOf { it.percentageText.toDoubleOrNull() ?: 0.0 }
                                val remaining = if (currentSum < 100.0) (100.0 - currentSum) else 50.0
                                val remStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else String.format(Locale.US, "%.1f", remaining)
                                carePayers = carePayers + CarePayerInput(name = "", percentageText = remStr)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zahler hinzufügen", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zahler", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    carePayers.forEachIndexed { index, payer ->
                        val payerPct = payer.percentageText.toDoubleOrNull() ?: 0.0
                        val payerCalculatedAmount = totalCareBase * (payerPct / 100.0)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Zahler ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    if (carePayers.size > 1) {
                                        IconButton(
                                            onClick = {
                                                carePayers = carePayers.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Zahler entfernen",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = payer.name,
                                    onValueChange = { newName ->
                                        carePayers = carePayers.toMutableList().apply {
                                            this[index] = this[index].copy(name = newName)
                                        }
                                        showError = false
                                    },
                                    label = { Text("Name des Zahlers / Kasse *") },
                                    placeholder = { Text("z.B. AOK Pflegekasse, Beihilfe, Barmer, Privat") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = payer.percentageText,
                                        onValueChange = { newPct ->
                                            carePayers = carePayers.toMutableList().apply {
                                                this[index] = this[index].copy(percentageText = newPct.replace(',', '.'))
                                            }
                                        },
                                        label = { Text("Anteil (%) *") },
                                        placeholder = { Text("100") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "Errechnete Summe:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = "+${fmt.format(payerCalculatedAmount)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card showing total computed payout from all payers
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auszahlungssumme aller Zahler:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "+${fmt.format(totalCarePayout)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            val pctFormatted = if (totalPayerPercentages % 1.0 == 0.0) totalPayerPercentages.toInt().toString() else String.format(Locale.US, "%.1f", totalPayerPercentages)
                            Text(
                                text = "Summe Anteile: $pctFormatted % von ${fmt.format(totalCareBase)} (${carePayers.size} ${if (carePayers.size == 1) "Zahler" else "Zahler"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (kotlin.math.abs(totalPayerPercentages - 100.0) < 0.01) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.replace(',', '.'); showError = false },
                        label = { Text("Betrag (€) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isOverride) {
                    Text("Gültigkeit / Rhythmus", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isRecurring, onClick = { isRecurring = true })
                            Text(if (isPflegegeld) "Monatlich wiederkehrend" else "Monatlich wiederkehrend (Standard-Gehalt)", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !isRecurring, onClick = { isRecurring = false })
                            Text("Einmalig / Spezieller Monat", fontSize = 14.sp)
                        }
                    }
                }

                if (!isRecurring || isOverride) {
                    Text("Betroffener Monat & Jahr", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expandedMonth by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedMonth,
                            onExpandedChange = { expandedMonth = !expandedMonth },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = PrintHelper.getGermanMonthName(specificMonth),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonth,
                                onDismissRequest = { expandedMonth = false }
                            ) {
                                (1..12).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(PrintHelper.getGermanMonthName(m)) },
                                        onClick = {
                                            specificMonth = m
                                            expandedMonth = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = specificYearText,
                            onValueChange = { specificYearText = it },
                            label = { Text("Jahr") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!isPflegegeld) {
                    CategoryDropdownSelector(
                        selectedCategory = category,
                        availableCategories = availableCategories,
                        defaultCategories = defaultCategories,
                        onCategorySelected = { category = it },
                        onQuickAddCategory = onQuickAddCategory,
                        onManageCategories = onManageCategories
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktiv")
                }

                if (showError) {
                    Text(
                        if (isPflegegeld) "Bitte Bezeichnung, mindestens einen Zahler mit Namen und Betrag eingeben."
                        else "Bitte Bezeichnung und Betrag eingeben.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedYear = specificYearText.toIntOrNull() ?: defaultSelectedYear

                if (isPflegegeld) {
                    val validPayers = carePayers.filter { it.name.isNotBlank() }
                    val finalAmount = totalCarePayout

                    if (title.isBlank() || validPayers.isEmpty() || finalAmount <= 0) {
                        showError = true
                    } else {
                        val payersList = carePayers.map { p ->
                            val pPct = p.percentageText.toDoubleOrNull() ?: 0.0
                            val pAmt = totalCareBase * (pPct / 100.0)
                            CarePayer(
                                name = p.name.trim(),
                                percentage = pPct,
                                calculatedAmount = pAmt
                            )
                        }
                        val payersJsonStr = serializeCarePayers(payersList)
                        val combinedSender = payersList.joinToString(", ") { p ->
                            val pPctStr = if (p.percentage % 1.0 == 0.0) p.percentage.toInt().toString() else p.percentage.toString()
                            "${p.name} ($pPctStr%)"
                        }

                        val inc = (initialIncome ?: Income(title = "", amount = 0.0, category = "Pflegegeld")).copy(
                            title = title.trim(),
                            amount = finalAmount,
                            isRecurring = if (isOverride) false else isRecurring,
                            specificYear = if (!isRecurring || isOverride) parsedYear else null,
                            specificMonth = if (!isRecurring || isOverride) specificMonth else null,
                            category = "Pflegegeld",
                            notes = notes.trim(),
                            isActive = isActive,
                            overriddenIncomeId = initialIncome?.overriddenIncomeId,
                            isPflegegeld = true,
                            sender = combinedSender,
                            pflegegrad = selectedPflegegrad,
                            pflegegeldBaseAmount = baseVal,
                            pflegehilfsmittelAmount = aidsVal,
                            percentageShare = totalPayerPercentages,
                            carePayersJson = payersJsonStr
                        )
                        onSave(inc)
                    }
                } else {
                    val finalAmount = amountText.toDoubleOrNull() ?: 0.0

                    if (title.isBlank() || finalAmount <= 0) {
                        showError = true
                    } else {
                        val inc = (initialIncome ?: Income(title = "", amount = 0.0, category = "")).copy(
                            title = title.trim(),
                            amount = finalAmount,
                            isRecurring = if (isOverride) false else isRecurring,
                            specificYear = if (!isRecurring || isOverride) parsedYear else null,
                            specificMonth = if (!isRecurring || isOverride) specificMonth else null,
                            category = category,
                            notes = notes.trim(),
                            isActive = isActive,
                            overriddenIncomeId = initialIncome?.overriddenIncomeId,
                            isPflegegeld = false,
                            sender = "",
                            pflegegrad = null,
                            pflegegeldBaseAmount = 0.0,
                            pflegehilfsmittelAmount = 0.0,
                            percentageShare = 100.0,
                            carePayersJson = ""
                        )
                        onSave(inc)
                    }
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
