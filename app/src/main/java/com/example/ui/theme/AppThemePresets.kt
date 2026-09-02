package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ThemePreset(
    val key: String,
    val displayName: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val containerColor: Color,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme
)

object AppThemePresets {
    // 1. Violett Eleganz (Standard)
    val PurpleElegance = ThemePreset(
        key = "PURPLE",
        displayName = "Violett Eleganz",
        description = "Klassischer Material 3 Stil mit edlem Amethyst & Lavendel",
        primaryColor = Color(0xFF6750A4),
        secondaryColor = Color(0xFF625B71),
        containerColor = Color(0xFFEADDFF),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD8E4),
            onTertiaryContainer = Color(0xFF31111D),
            background = Color(0xFFF6F2FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE7E0EC)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF492532),
            tertiaryContainer = Color(0xFF633B48),
            onTertiaryContainer = Color(0xFFFFD8E4),
            background = Color(0xFF141218),
            surface = Color(0xFF211F26),
            surfaceVariant = Color(0xFF49454F)
        )
    )

    // 2. Ozean Blau
    val OceanBlue = ThemePreset(
        key = "OCEAN",
        displayName = "Ozean Blau",
        description = "Frisches Saphirblau und kühles Türkis für klare Finanzen",
        primaryColor = Color(0xFF00639A),
        secondaryColor = Color(0xFF006874),
        containerColor = Color(0xFFC2E8FF),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF00639A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC2E8FF),
            onPrimaryContainer = Color(0xFF001D33),
            secondary = Color(0xFF006874),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF97F0FF),
            onSecondaryContainer = Color(0xFF001F24),
            tertiary = Color(0xFF00687A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFABEFFF),
            onTertiaryContainer = Color(0xFF001F26),
            background = Color(0xFFF2F8FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDDE3EA)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF98CAFF),
            onPrimary = Color(0xFF003254),
            primaryContainer = Color(0xFF004A76),
            onPrimaryContainer = Color(0xFFC2E8FF),
            secondary = Color(0xFF4FD8EB),
            onSecondary = Color(0xFF00363D),
            tertiary = Color(0xFF80D4EA),
            onTertiary = Color(0xFF003641),
            tertiaryContainer = Color(0xFF004E5D),
            onTertiaryContainer = Color(0xFFABEFFF),
            background = Color(0xFF0F1417),
            surface = Color(0xFF1B2125),
            surfaceVariant = Color(0xFF41484D)
        )
    )

    // 3. Smaragd Grün
    val EmeraldGreen = ThemePreset(
        key = "EMERALD",
        displayName = "Smaragd Grün",
        description = "Harmonisches Waldgrün & Minze für positiven Kontostand",
        primaryColor = Color(0xFF006C4C),
        secondaryColor = Color(0xFF4D6356),
        containerColor = Color(0xFF89F8C7),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF006C4C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF89F8C7),
            onPrimaryContainer = Color(0xFF002114),
            secondary = Color(0xFF4D6356),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCFE9D7),
            onSecondaryContainer = Color(0xFF0B1F15),
            tertiary = Color(0xFF006B5D),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF82F8E2),
            onTertiaryContainer = Color(0xFF00201B),
            background = Color(0xFFF3F9F5),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCE5DD)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF6CDBAC),
            onPrimary = Color(0xFF003825),
            primaryContainer = Color(0xFF005238),
            onPrimaryContainer = Color(0xFF89F8C7),
            secondary = Color(0xFFB3CCBC),
            onSecondary = Color(0xFF203529),
            tertiary = Color(0xFF64DBC6),
            onTertiary = Color(0xFF00372F),
            tertiaryContainer = Color(0xFF005045),
            onTertiaryContainer = Color(0xFF82F8E2),
            background = Color(0xFF0F1512),
            surface = Color(0xFF1B221E),
            surfaceVariant = Color(0xFF404943)
        )
    )

    // 4. Sonnenuntergang
    val SunsetWarm = ThemePreset(
        key = "SUNSET",
        displayName = "Sonnenuntergang",
        description = "Warme Bernsteintöne & Terrakotta für Energie & Übersicht",
        primaryColor = Color(0xFF9C4300),
        secondaryColor = Color(0xFF77574B),
        containerColor = Color(0xFFFFDCC9),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF9C4300),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC9),
            onPrimaryContainer = Color(0xFF341100),
            secondary = Color(0xFF77574B),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDBCF),
            onSecondaryContainer = Color(0xFF2C160C),
            tertiary = Color(0xFF8C4E00),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFDCC1),
            onTertiaryContainer = Color(0xFF2E1500),
            background = Color(0xFFFCF7F5),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF4E0D6)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFFFB590),
            onPrimary = Color(0xFF552000),
            primaryContainer = Color(0xFF783100),
            onPrimaryContainer = Color(0xFFFFDCC9),
            secondary = Color(0xFFE7BEAF),
            onSecondary = Color(0xFF442A20),
            tertiary = Color(0xFFFFB77C),
            onTertiary = Color(0xFF4B2800),
            tertiaryContainer = Color(0xFF6B3B00),
            onTertiaryContainer = Color(0xFFFFDCC1),
            background = Color(0xFF1A120E),
            surface = Color(0xFF271C17),
            surfaceVariant = Color(0xFF53433C)
        )
    )

    // 5. Mitternacht Dark
    val MidnightDark = ThemePreset(
        key = "MIDNIGHT",
        displayName = "Mitternacht Indigo",
        description = "Moderner, augenschonender Dark-Look mit Leucht-Indigoblau",
        primaryColor = Color(0xFF7B52FF),
        secondaryColor = Color(0xFF00C8FF),
        containerColor = Color(0xFF2B2050),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF5A32E6),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE5DEFF),
            onPrimaryContainer = Color(0xFF190066),
            secondary = Color(0xFF00688B),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFC3E8FF),
            tertiary = Color(0xFF00688B),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFC3E8FF),
            onTertiaryContainer = Color(0xFF001E2B),
            background = Color(0xFFF6F5FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E0EE)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFC9B8FF),
            onPrimary = Color(0xFF2F009A),
            primaryContainer = Color(0xFF4519CF),
            onPrimaryContainer = Color(0xFFE5DEFF),
            secondary = Color(0xFF78D1FF),
            onSecondary = Color(0xFF003549),
            tertiary = Color(0xFF78D1FF),
            onTertiary = Color(0xFF003549),
            tertiaryContainer = Color(0xFF004C68),
            onTertiaryContainer = Color(0xFFC3E8FF),
            background = Color(0xFF0B0914),
            surface = Color(0xFF161324),
            surfaceVariant = Color(0xFF2E2942)
        )
    )

    // 6. Klassisch Graphit
    val ClassicSlate = ThemePreset(
        key = "CLASSIC",
        displayName = "Klassisch Graphit",
        description = "Neutraler, starker Kontrast in Schiefergrau & Anthrazit",
        primaryColor = Color(0xFF384353),
        secondaryColor = Color(0xFF556070),
        containerColor = Color(0xFFDAE2F0),
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF384353),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDAE2F0),
            onPrimaryContainer = Color(0xFF0F1A28),
            secondary = Color(0xFF556070),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD9E3F4),
            tertiary = Color(0xFF326374),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFB7E8FA),
            onTertiaryContainer = Color(0xFF001F29),
            background = Color(0xFFF4F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE0E4E9)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFBEC6D4),
            onPrimary = Color(0xFF212C3C),
            primaryContainer = Color(0xFF384353),
            onPrimaryContainer = Color(0xFFDAE2F0),
            secondary = Color(0xFFBDC7D8),
            tertiary = Color(0xFF9BCEE2),
            onTertiary = Color(0xFF003544),
            tertiaryContainer = Color(0xFF164B5B),
            onTertiaryContainer = Color(0xFFB7E8FA),
            background = Color(0xFF111418),
            surface = Color(0xFF1B1E23),
            surfaceVariant = Color(0xFF414750)
        )
    )

    val allPresets = listOf(
        PurpleElegance,
        OceanBlue,
        EmeraldGreen,
        SunsetWarm,
        MidnightDark,
        ClassicSlate
    )

    fun getPresetByKey(key: String): ThemePreset {
        return allPresets.find { it.key == key } ?: PurpleElegance
    }
}
