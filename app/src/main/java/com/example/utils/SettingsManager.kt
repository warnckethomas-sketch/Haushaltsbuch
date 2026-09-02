package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.ui.theme.AppThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("haushaltsbuch_settings", Context.MODE_PRIVATE)

    private val _themeKey = MutableStateFlow(prefs.getString(KEY_THEME, "PURPLE") ?: "PURPLE")
    val themeKey: StateFlow<String> = _themeKey.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(
        if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, false) else null
    )
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupUri = MutableStateFlow(prefs.getString(KEY_AUTO_BACKUP_URI, null))
    val autoBackupUri: StateFlow<String?> = _autoBackupUri.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getString(KEY_LAST_BACKUP_TIME, null))
    val lastBackupTime: StateFlow<String?> = _lastBackupTime.asStateFlow()

    private val _backupStatus = MutableStateFlow(prefs.getString(KEY_BACKUP_STATUS, null))
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    // Pflegegeld & Hilfsmittel Configurable Defaults
    private val _pg1Amount = MutableStateFlow(prefs.getFloat(KEY_PG_1, 0.0f).toDouble())
    val pg1Amount: StateFlow<Double> = _pg1Amount.asStateFlow()

    private val _pg2Amount = MutableStateFlow(prefs.getFloat(KEY_PG_2, 332.0f).toDouble())
    val pg2Amount: StateFlow<Double> = _pg2Amount.asStateFlow()

    private val _pg3Amount = MutableStateFlow(prefs.getFloat(KEY_PG_3, 572.0f).toDouble())
    val pg3Amount: StateFlow<Double> = _pg3Amount.asStateFlow()

    private val _pg4Amount = MutableStateFlow(prefs.getFloat(KEY_PG_4, 765.0f).toDouble())
    val pg4Amount: StateFlow<Double> = _pg4Amount.asStateFlow()

    private val _pg5Amount = MutableStateFlow(prefs.getFloat(KEY_PG_5, 947.0f).toDouble())
    val pg5Amount: StateFlow<Double> = _pg5Amount.asStateFlow()

    private val _hilfsmittelAmount = MutableStateFlow(prefs.getFloat(KEY_HILFSMITTEL, 42.0f).toDouble())
    val hilfsmittelAmount: StateFlow<Double> = _hilfsmittelAmount.asStateFlow()

    fun getPflegegradAmount(grad: Int): Double {
        return when (grad) {
            1 -> _pg1Amount.value
            2 -> _pg2Amount.value
            3 -> _pg3Amount.value
            4 -> _pg4Amount.value
            5 -> _pg5Amount.value
            else -> 0.0
        }
    }

    fun setPflegegradAmount(grad: Int, amount: Double) {
        val f = amount.toFloat()
        when (grad) {
            1 -> { prefs.edit().putFloat(KEY_PG_1, f).apply(); _pg1Amount.value = amount }
            2 -> { prefs.edit().putFloat(KEY_PG_2, f).apply(); _pg2Amount.value = amount }
            3 -> { prefs.edit().putFloat(KEY_PG_3, f).apply(); _pg3Amount.value = amount }
            4 -> { prefs.edit().putFloat(KEY_PG_4, f).apply(); _pg4Amount.value = amount }
            5 -> { prefs.edit().putFloat(KEY_PG_5, f).apply(); _pg5Amount.value = amount }
        }
    }

    fun getHilfsmittelAmount(): Double = _hilfsmittelAmount.value

    fun setHilfsmittelAmount(amount: Double) {
        prefs.edit().putFloat(KEY_HILFSMITTEL, amount.toFloat()).apply()
        _hilfsmittelAmount.value = amount
    }

    fun resetPflegeDefaultsToStandard() {
        setPflegegradAmount(1, 0.0)
        setPflegegradAmount(2, 332.0)
        setPflegegradAmount(3, 572.0)
        setPflegegradAmount(4, 765.0)
        setPflegegradAmount(5, 947.0)
        setHilfsmittelAmount(42.0)
    }

    fun setThemeKey(key: String) {
        prefs.edit().putString(KEY_THEME, key).apply()
        _themeKey.value = key
    }

    fun setDarkMode(dark: Boolean?) {
        if (dark == null) {
            prefs.edit().remove(KEY_DARK_MODE).apply()
        } else {
            prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
        }
        _isDarkMode.value = dark
    }

    fun setAutoBackupTarget(uri: Uri?) {
        if (uri != null) {
            val uriString = uri.toString()
            prefs.edit()
                .putString(KEY_AUTO_BACKUP_URI, uriString)
                .putBoolean(KEY_AUTO_BACKUP_ENABLED, true)
                .apply()
            _autoBackupUri.value = uriString
            _autoBackupEnabled.value = true
        } else {
            prefs.edit()
                .remove(KEY_AUTO_BACKUP_URI)
                .putBoolean(KEY_AUTO_BACKUP_ENABLED, false)
                .apply()
            _autoBackupUri.value = null
            _autoBackupEnabled.value = false
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun performAutoBackupIfEnabled(jsonString: String) {
        if (!_autoBackupEnabled.value) return
        val uriStr = _autoBackupUri.value ?: return

        try {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())
            val statusMsg = "Automatisch gesichert um $timeStr"

            prefs.edit()
                .putString(KEY_LAST_BACKUP_TIME, timeStr)
                .putString(KEY_BACKUP_STATUS, statusMsg)
                .apply()

            _lastBackupTime.value = timeStr
            _backupStatus.value = statusMsg
        } catch (e: Exception) {
            val errStr = "Fehler bei automatischer Sicherung: ${e.localizedMessage}"
            prefs.edit().putString(KEY_BACKUP_STATUS, errStr).apply()
            _backupStatus.value = errStr
        }
    }

    fun getFriendlyStorageName(uriString: String?): String {
        if (uriString.isNullOrEmpty()) return "Kein Ziel gewählt"
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return uriString }

        var fileName: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        fileName = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}

        val authority = uri.authority ?: ""
        val decodedPath = Uri.decode(uri.path ?: "")

        val locationName = when {
            authority.contains("google.android.apps.docs", ignoreCase = true) -> "Google Drive"
            authority.contains("externalstorage", ignoreCase = true) -> {
                if (decodedPath.contains("primary:", ignoreCase = true)) {
                    "Interner Speicher"
                } else if (decodedPath.contains(":")) {
                    val vol = decodedPath.substringBefore(":", "").substringAfterLast("/")
                    if (vol.isNotBlank()) "SD-Karte ($vol)" else "SD-Karte"
                } else {
                    "Interner Speicher"
                }
            }
            authority.contains("downloads", ignoreCase = true) -> "Downloads"
            authority.contains("media", ignoreCase = true) -> "Medienspeicher"
            authority.contains("onedrive", ignoreCase = true) -> "OneDrive"
            authority.contains("dropbox", ignoreCase = true) -> "Dropbox"
            else -> "Laufwerk"
        }

        // Extract subpath from SAF document/tree URI
        var rawSubPath = when {
            decodedPath.contains("/document/") -> decodedPath.substringAfterLast("/document/")
            decodedPath.contains("/tree/") -> decodedPath.substringAfterLast("/tree/")
            else -> decodedPath
        }

        // Remove volume specifier if present (e.g., "primary:" or volume ID)
        if (rawSubPath.contains(":")) {
            rawSubPath = rawSubPath.substringAfter(":")
        }

        rawSubPath = rawSubPath.trim('/')

        // Parse path segments, removing technical tokens if any
        val rawSegments = rawSubPath.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("doc=") && !it.startsWith("acc=") && !it.contains("content://") }

        if (fileName.isNullOrBlank()) {
            fileName = rawSegments.lastOrNull()
        }

        if (fileName.isNullOrBlank() || fileName.contains("%") || fileName.length > 80) {
            fileName = "haushaltsbuch_sicherung_auto.json"
        }

        // Extract intermediate folder segments
        val folderSegments = mutableListOf<String>()
        for (seg in rawSegments) {
            // Ignore the exact filename or trailing .json file in folder list
            if (seg.equals(fileName, ignoreCase = true) || seg.endsWith(".json", ignoreCase = true)) {
                continue
            }
            // Filter out long random hex/hash strings without spaces
            if (seg.length < 32 || seg.contains(" ") || seg.contains("-") && !seg.contains("0x")) {
                folderSegments.add(seg)
            }
        }

        val result = StringBuilder(locationName)
        for (folder in folderSegments) {
            result.append(" › ").append(folder)
        }
        result.append(" › ").append(fileName)

        return result.toString()
    }

    companion object {
        private const val KEY_THEME = "theme_key"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_URI = "auto_backup_uri"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_BACKUP_STATUS = "backup_status"

        private const val KEY_PG_1 = "pf_pg_1"
        private const val KEY_PG_2 = "pf_pg_2"
        private const val KEY_PG_3 = "pf_pg_3"
        private const val KEY_PG_4 = "pf_pg_4"
        private const val KEY_PG_5 = "pf_pg_5"
        private const val KEY_HILFSMITTEL = "pf_hilfsmittel"
    }
}
