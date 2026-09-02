package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.Category
import com.example.data.entities.CategoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    categories: List<Category>,
    initialType: String = CategoryType.FIXED_COST,
    onDismiss: () -> Unit,
    onAddCategory: (String, String) -> Unit,
    onUpdateCategory: (Category, String) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    var selectedTabType by remember { mutableStateOf(initialType) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    val tabTypes = listOf(
        CategoryType.FIXED_COST to "Fixkosten",
        CategoryType.RECURRING_EXPENSE to "Wiederkehrend",
        CategoryType.INVOICE to "Rechnungen",
        CategoryType.INCOME to "Einnahmen"
    )

    val currentCategories = remember(categories, selectedTabType) {
        categories.filter { it.type == selectedTabType }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Kategorien verwalten", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                // Scrollable TabRow for Category Types
                ScrollableTabRow(
                    selectedTabIndex = tabTypes.indexOfFirst { it.first == selectedTabType }.coerceAtLeast(0),
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTypes.forEach { (typeKey, typeLabel) ->
                        Tab(
                            selected = selectedTabType == typeKey,
                            onClick = { selectedTabType = typeKey },
                            text = { Text(typeLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add Category Button
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Neue Kategorie in '${tabTypes.firstOrNull { it.first == selectedTabType }?.second}' anlegen",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (currentCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keine Kategorien vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentCategories, key = { it.id }) { cat ->
                            CategoryItemRow(
                                category = cat,
                                onEdit = { editingCategory = cat },
                                onDelete = { deletingCategory = cat }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fertig")
            }
        }
    )

    // --- Sub-Dialog: Add Category ---
    if (showAddDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Neue Kategorie anlegen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bereich: ${tabTypes.firstOrNull { it.first == selectedTabType }?.second}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
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
                        if (newCategoryName.isBlank()) {
                            isError = true
                        } else {
                            onAddCategory(newCategoryName.trim(), selectedTabType)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Anlegen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // --- Sub-Dialog: Edit/Rename Category ---
    editingCategory?.let { cat ->
        var updatedName by remember { mutableStateOf(cat.name) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Kategorie bearbeiten", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Einträge mit der alten Kategorie '${cat.name}' werden automatisch aktualisiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = updatedName,
                        onValueChange = {
                            updatedName = it
                            isError = false
                        },
                        label = { Text("Neuer Name *") },
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
                        if (updatedName.isBlank()) {
                            isError = true
                        } else {
                            onUpdateCategory(cat, updatedName.trim())
                            editingCategory = null
                        }
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // --- Sub-Dialog: Delete Confirmation ---
    deletingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Kategorie löschen?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Möchten Sie die Kategorie '${cat.name}' wirklich löschen?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(cat)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun CategoryItemRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Kategorie bearbeiten",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Kategorie löschen",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
