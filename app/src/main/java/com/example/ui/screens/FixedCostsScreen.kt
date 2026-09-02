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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.Category
import com.example.data.entities.CategoryType
import com.example.data.entities.FixedCost
import com.example.ui.components.AddEditFixedCostDialog
import com.example.ui.components.PrintHelper
import com.example.ui.theme.ExpenseRed
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedCostsScreen(
    fixedCosts: List<FixedCost>,
    categories: List<Category> = emptyList(),
    onSaveCost: (FixedCost) -> Unit,
    onToggleActive: (FixedCost) -> Unit,
    onDeleteCost: (FixedCost) -> Unit,
    onAddCategory: (String, String) -> Unit = { _, _ -> },
    onManageCategories: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingCost by remember { mutableStateOf<FixedCost?>(null) }
    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    val fixedCategories = categories.filter { it.type == CategoryType.FIXED_COST }.map { it.name }

    val activeCosts = fixedCosts.filter { it.isActive }
    val totalMonthlyAmount = activeCosts.sumOf { it.amount }

    val groupedCosts = remember(fixedCosts) {
        fixedCosts.sortedBy { it.title }.groupBy {
            if (it.category.isBlank()) "Sonstiges" else it.category
        }.toSortedMap()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCost = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_fixed_cost_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Fixkosten hinzufügen")
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

            // Total Monthly Fixed Costs Header Card
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
                            text = "Monatliche Fixkosten",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeCosts.size} von ${fixedCosts.size} Positionen aktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "-${fmt.format(totalMonthlyAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "pro Monat (gesamt)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (fixedCosts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Noch keine Fixkosten erfasst",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Klicke auf '+' um Miete, Strom oder Abos hinzuzufügen.",
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
                    groupedCosts.forEach { (category, categoryCosts) ->
                        item(key = "header_$category") {
                            val activeCatCosts = categoryCosts.filter { it.isActive }
                            val totalCatMonthly = activeCatCosts.sumOf { it.amount }

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
                                            text = "(${categoryCosts.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Text(
                                        text = "-${fmt.format(totalCatMonthly)} / Monat",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        items(categoryCosts, key = { it.id }) { item ->
                            FixedCostCard(
                                cost = item,
                                fmt = fmt,
                                onToggleActive = { onToggleActive(item) },
                                onEdit = {
                                    editingCost = item
                                    showDialog = true
                                },
                                onDelete = { onDeleteCost(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddEditFixedCostDialog(
            initialCost = editingCost,
            availableCategories = fixedCategories,
            onDismiss = { showDialog = false },
            onSave = { cost ->
                onSaveCost(cost)
                showDialog = false
            },
            onManageCategories = onManageCategories,
            onQuickAddCategory = { name ->
                onAddCategory(name, CategoryType.FIXED_COST)
            }
        )
    }
}

@Composable
fun FixedCostCard(
    cost: FixedCost,
    fmt: NumberFormat,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (cost.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val dueText = if (cost.endYear != null && cost.endMonth != null) {
                        "Fällig: ${cost.dueDayOfMonth}. des Monats • Bis ${PrintHelper.getGermanMonthName(cost.endMonth)} ${cost.endYear}"
                    } else {
                        "Fällig: ${cost.dueDayOfMonth}. des Monats"
                    }
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = cost.isActive,
                    onCheckedChange = { onToggleActive() },
                    modifier = Modifier.height(30.dp)
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
                        text = cost.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (cost.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (cost.notes.isNotBlank()) {
                        Text(
                            text = cost.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "-${fmt.format(cost.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cost.isActive) ExpenseRed else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false
                )
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


@Composable
fun Modifier.scale08(): Modifier = this.padding(0.dp)
