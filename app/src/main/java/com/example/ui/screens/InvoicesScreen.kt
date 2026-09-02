package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.Category
import com.example.data.entities.CategoryType
import com.example.data.entities.Invoice
import com.example.ui.components.AddEditInvoiceDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.WarningOrange
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    invoices: List<Invoice>,
    categories: List<Category> = emptyList(),
    onSaveInvoice: (Invoice) -> Unit,
    onTogglePaid: (Invoice) -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onAddCategory: (String, String) -> Unit = { _, _ -> },
    onManageCategories: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var filterStatus by remember { mutableStateOf("Alle") } // "Alle", "Offen", "Bezahlt"

    val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    val invoiceCategories = categories.filter { it.type == CategoryType.INVOICE }.map { it.name }

    val filteredInvoices = remember(invoices, filterStatus) {
        when (filterStatus) {
            "Offen" -> invoices.filter { !it.isPaid }
            "Bezahlt" -> invoices.filter { it.isPaid }
            else -> invoices
        }
    }

    val totalOpenAmount = invoices.filter { !it.isPaid }.sumOf { it.amount }
    val openCount = invoices.count { !it.isPaid }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingInvoice = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_invoice_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Neue Rechnung erfassen")
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

            // Open Invoices Header KPI Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (openCount > 0) WarningOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                    Column {
                        Text(
                            text = "Offene Rechnungen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$openCount offene Rechnungen ausstehend",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = fmt.format(totalOpenAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (openCount > 0) WarningOrange else IncomeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Alle", "Offen", "Bezahlt").forEach { status ->
                    FilterChip(
                        selected = filterStatus == status,
                        onClick = { filterStatus = status },
                        label = { Text(status) },
                        leadingIcon = if (filterStatus == status) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keine Rechnungen in dieser Ansicht",
                            style = MaterialTheme.typography.titleMedium,
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
                    items(filteredInvoices, key = { it.id }) { item ->
                        InvoiceCard(
                            invoice = item,
                            fmt = fmt,
                            onTogglePaid = { onTogglePaid(item) },
                            onEdit = {
                                editingInvoice = item
                                showDialog = true
                            },
                            onDelete = { onDeleteInvoice(item) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddEditInvoiceDialog(
            initialInvoice = editingInvoice,
            availableCategories = invoiceCategories,
            onDismiss = { showDialog = false },
            onSave = { inv ->
                onSaveInvoice(inv)
                showDialog = false
            },
            onManageCategories = onManageCategories,
            onQuickAddCategory = { name ->
                onAddCategory(name, CategoryType.INVOICE)
            }
        )
    }
}

@Composable
fun InvoiceCard(
    invoice: Invoice,
    fmt: NumberFormat,
    onTogglePaid: () -> Unit,
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(invoice.category, fontSize = 11.sp) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (invoice.invoiceNumber.isNotBlank()) {
                        Text(
                            text = "Nr. ${invoice.invoiceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    onClick = onTogglePaid,
                    shape = MaterialTheme.shapes.small,
                    color = if (invoice.isPaid) IncomeGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (invoice.isPaid) Icons.Default.CheckCircle else Icons.Default.Pending,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (invoice.isPaid) IncomeGreen else WarningOrange
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (invoice.isPaid) "BEZAHLT" else "OFFEN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.isPaid) IncomeGreen else WarningOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (invoice.vendor.isNotBlank()) {
                        Text(
                            text = "Rechnungssteller: ${invoice.vendor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Fällig am: ${invoice.getDueDateFormatted()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!invoice.isPaid) ExpenseRed else MaterialTheme.colorScheme.outline
                    )
                    if (invoice.notes.isNotBlank()) {
                        Text(
                            text = invoice.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Text(
                    text = "-${fmt.format(invoice.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTogglePaid) {
                    Icon(
                        imageVector = if (invoice.isPaid) Icons.Default.Undo else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (invoice.isPaid) "Als offen markieren" else "Als bezahlt markieren", fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
