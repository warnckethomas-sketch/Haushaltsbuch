package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemePresets
import com.example.ui.theme.ThemePreset
import com.example.utils.SettingsManager
import java.io.InputStream
import java.io.OutputStream

private enum class SetupSection {
    PFLEGE, BACKUP, DESIGN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndBackupDialog(
    settingsManager: SettingsManager,
    currentThemeKey: String,
    isDarkMode: Boolean?,
    autoBackupEnabled: Boolean,
    autoBackupUri: String?,
    lastBackupTime: String?,
    backupStatus: String?,
    onExportManualJson: () -> String,
    onImportJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var expandedSection by remember { mutableStateOf<SetupSection?>(null) }
    val isPflegeExpanded = expandedSection == SetupSection.PFLEGE
    val isBackupExpanded = expandedSection == SetupSection.BACKUP
    val isDesignExpanded = expandedSection == SetupSection.DESIGN

    val pg1Val by settingsManager.pg1Amount.collectAsState()
    val pg2Val by settingsManager.pg2Amount.collectAsState()
    val pg3Val by settingsManager.pg3Amount.collectAsState()
    val pg4Val by settingsManager.pg4Amount.collectAsState()
    val pg5Val by settingsManager.pg5Amount.collectAsState()
    val hilfsmittelVal by settingsManager.hilfsmittelAmount.collectAsState()

    var editPg1 by remember(pg1Val) { mutableStateOf(pg1Val.toString()) }
    var editPg2 by remember(pg2Val) { mutableStateOf(pg2Val.toString()) }
    var editPg3 by remember(pg3Val) { mutableStateOf(pg3Val.toString()) }
    var editPg4 by remember(pg4Val) { mutableStateOf(pg4Val.toString()) }
    var editPg5 by remember(pg5Val) { mutableStateOf(pg5Val.toString()) }
    var editHilfsmittel by remember(hilfsmittelVal) { mutableStateOf(hilfsmittelVal.toString()) }

    // SAF Document Creator for selecting auto-backup target file on physical drive
    val autoBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            settingsManager.setAutoBackupTarget(uri)
            val json = onExportManualJson()
            settingsManager.performAutoBackupIfEnabled(json)
            Toast.makeText(context, "Speicherpfad festgelegt & Erstsicherung erstellt!", Toast.LENGTH_LONG).show()
        }
    }

    // SAF Manual Export Launcher
    val manualExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = onExportManualJson()
                context.contentResolver.openOutputStream(uri)?.use { os: OutputStream ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Sicherung erfolgreich exportiert!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Exportieren: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // SAF Manual Import Launcher
    val manualImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { isStream: InputStream ->
                    val content = isStream.bufferedReader().use { it.readText() }
                    onImportJson(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Importieren: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Einstellungen & Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. PFLEGE-STANDARDS ACCORDION CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPflegeExpanded) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (expandedSection == SetupSection.PFLEGE) null else SetupSection.PFLEGE }
                                .padding(12.dp)
                                .testTag("tab_pflege_setup"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "🏥 Pflege-Standards & Beträge",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Sätze für Pflegegrade (1-5) & Hilfsmittel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isPflegeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = isPflegeExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()
                                Text(
                                    "Anpassung der Standardwerte für Pflegegeld",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Bei gesetzlichen Änderungen oder Neuerungen kannst Du die Standard-Sätze für Pflegegrade und Hilfsmittel hier zentral anpassen. Neue Pflegegeld-Einträge nutzen automatisch diese Werte.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editPg1,
                                            onValueChange = { editPg1 = it.replace(',', '.') },
                                            label = { Text("Pflegegrad 1 (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editPg2,
                                            onValueChange = { editPg2 = it.replace(',', '.') },
                                            label = { Text("Pflegegrad 2 (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editPg3,
                                            onValueChange = { editPg3 = it.replace(',', '.') },
                                            label = { Text("Pflegegrad 3 (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editPg4,
                                            onValueChange = { editPg4 = it.replace(',', '.') },
                                            label = { Text("Pflegegrad 4 (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editPg5,
                                            onValueChange = { editPg5 = it.replace(',', '.') },
                                            label = { Text("Pflegegrad 5 (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editHilfsmittel,
                                            onValueChange = { editHilfsmittel = it.replace(',', '.') },
                                            label = { Text("Hilfsmittel (€)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val v1 = editPg1.toDoubleOrNull() ?: 0.0
                                        val v2 = editPg2.toDoubleOrNull() ?: 332.0
                                        val v3 = editPg3.toDoubleOrNull() ?: 572.0
                                        val v4 = editPg4.toDoubleOrNull() ?: 765.0
                                        val v5 = editPg5.toDoubleOrNull() ?: 947.0
                                        val vH = editHilfsmittel.toDoubleOrNull() ?: 42.0

                                        settingsManager.setPflegegradAmount(1, v1)
                                        settingsManager.setPflegegradAmount(2, v2)
                                        settingsManager.setPflegegradAmount(3, v3)
                                        settingsManager.setPflegegradAmount(4, v4)
                                        settingsManager.setPflegegradAmount(5, v5)
                                        settingsManager.setHilfsmittelAmount(vH)

                                        Toast.makeText(context, "Pflege-Standardwerte gespeichert!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Standardwerte speichern")
                                }

                                OutlinedButton(
                                    onClick = {
                                        settingsManager.resetPflegeDefaultsToStandard()
                                        Toast.makeText(context, "Auf gesetzliche Standards zurückgesetzt!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auf Gesetzliche Standards zurücksetzen")
                                }
                            }
                        }
                    }
                }

                // 2. DATENSICHERUNG ACCORDION CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBackupExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (expandedSection == SetupSection.BACKUP) null else SetupSection.BACKUP }
                                .padding(12.dp)
                                .testTag("tab_backup_setup"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SdCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "💾 Datensicherung & Auto-Export",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (autoBackupUri != null) "Automatische Sicherung aktiv" else "Manuelle Sicherung & Laufwerk-Ziel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isBackupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = isBackupExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()
                                Text(
                                    "Automatisches Überschreiben auf Laufwerk",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Wähle eine Datei auf Deinem physischen Speicher / USB / SD-Karte oder Ordner. Alle neuen Eingaben überschreiben diese Datei automatisch.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        autoBackupLauncher.launch("haushaltsbuch_sicherung_auto.json")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("choose_drive_file_button")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (autoBackupUri != null) "Speicherdatei / Laufwerk ändern" else "Laufwerk & Speicherdatei wählen")
                                }

                                if (autoBackupUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Automatische Sicherung aktiv", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Switch(
                                            checked = autoBackupEnabled,
                                            onCheckedChange = { settingsManager.setAutoBackupEnabled(it) }
                                        )
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (autoBackupUri.contains("docs", ignoreCase = true)) Icons.Default.CloudQueue else Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Ziel: ${settingsManager.getFriendlyStorageName(autoBackupUri)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (lastBackupTime != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = backupStatus ?: "Letztes Backup: $lastBackupTime",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Text("Manuelle Sicherung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { manualExportLauncher.launch("haushaltsbuch_sicherung.json") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("manual_export_button")
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export (.json)")
                                    }

                                    OutlinedButton(
                                        onClick = { manualImportLauncher.launch(arrayOf("application/json", "*/*")) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("manual_import_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Importieren")
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. FARBDESIGN ACCORDION CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDesignExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (expandedSection == SetupSection.DESIGN) null else SetupSection.DESIGN }
                                .padding(12.dp)
                                .testTag("tab_theme_setup"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "🎨 Farbdesign & Erscheinungsbild",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dunkler Modus & Farbschemata",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isDesignExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = isDesignExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Dunkler Modus (Dark Theme)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = when (isDarkMode) {
                                                true -> "Dunkler Modus erzwungen"
                                                false -> "Heller Modus erzwungen"
                                                null -> "Systemstandard"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = isDarkMode == false,
                                            onClick = { settingsManager.setDarkMode(false) },
                                            label = { Text("Hell", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = isDarkMode == true,
                                            onClick = { settingsManager.setDarkMode(true) },
                                            label = { Text("Dunkel", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = isDarkMode == null,
                                            onClick = { settingsManager.setDarkMode(null) },
                                            label = { Text("Auto", fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Text("Farbdesign-Vorschläge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppThemePresets.allPresets.forEach { preset ->
                                        ThemePresetCard(
                                            preset = preset,
                                            isSelected = preset.key == currentThemeKey,
                                            onSelect = { settingsManager.setThemeKey(preset.key) }
                                        )
                                    }
                                }
                            }
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
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Color Circles Palette Preview
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(preset.primaryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(preset.secondaryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(preset.containerColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}
