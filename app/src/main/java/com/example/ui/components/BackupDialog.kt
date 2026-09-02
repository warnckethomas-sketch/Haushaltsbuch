package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.*
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun BackupDialog(
    categoriesCount: Int,
    fixedCostsCount: Int,
    recurringExpensesCount: Int,
    invoicesCount: Int,
    incomesCount: Int,
    onDismiss: () -> Unit,
    onGenerateBackupJson: () -> String,
    onRestoreBackupJson: (String, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Launcher to save backup file
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = onGenerateBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                statusMessage = "Sicherung erfolgreich in Datei gespeichert!"
                isError = false
                Toast.makeText(context, "Datei erfolgreich gespeichert", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                statusMessage = "Fehler beim Speichern: ${e.localizedMessage}"
                isError = true
            }
        }
    }

    // Launcher to open/restore backup file
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val jsonString = reader.use { it.readText() }

                onRestoreBackupJson(jsonString) { success, error ->
                    if (success) {
                        statusMessage = "Daten erfolgreich wiederhergestellt!"
                        isError = false
                        Toast.makeText(context, "Daten erfolgreich importiert!", Toast.LENGTH_LONG).show()
                    } else {
                        statusMessage = "Fehler beim Wiederherstellen: $error"
                        isError = true
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Fehler beim Lesen der Datei: ${e.localizedMessage}"
                isError = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Datensicherung & Import", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Aktueller Datenbestand:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• $incomesCount Einnahmen / Gehälter", fontSize = 12.sp)
                        Text("• $fixedCostsCount Fixkosten", fontSize = 12.sp)
                        Text("• $recurringExpensesCount Wiederkehrende Ausgaben", fontSize = 12.sp)
                        Text("• $invoicesCount Rechnungen", fontSize = 12.sp)
                        Text("• $categoriesCount Kategorien", fontSize = 12.sp)
                    }
                }

                if (statusMessage != null) {
                    Surface(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text("Aktionen:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                // Export / Save button
                Button(
                    onClick = {
                        val fileName = "Haushaltsbuch_Backup_${System.currentTimeMillis() / 1000}.json"
                        saveLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_backup_button"),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle Einträge in Datei sichern (.json)")
                }

                // Share button
                OutlinedButton(
                    onClick = {
                        try {
                            val json = onGenerateBackupJson()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Haushaltsbuch Sicherung")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Sicherung teilen"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Fehler: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("share_backup_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup als Text teilen")
                }

                Divider()

                // Import / Restore button
                OutlinedButton(
                    onClick = { openLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth().testTag("import_backup_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aus Datei wiederherstellen")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_backup_dialog")
            ) {
                Text("Schließen")
            }
        }
    )
}
