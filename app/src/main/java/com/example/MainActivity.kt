package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.example.ui.components.CategoryManagementDialog
import com.example.ui.components.PrintHelper
import com.example.ui.components.SettingsAndBackupDialog
import com.example.ui.screens.*
import com.example.ui.theme.HaushaltsbuchTheme
import com.example.ui.viewmodel.FinanceViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object FixedCosts : Screen("fixed_costs", "Fixkosten", Icons.Default.ReceiptLong)
    object RecurringExpenses : Screen("recurring", "Wiederkehrend", Icons.Default.EventRepeat)
    object Invoices : Screen("invoices", "Rechnungen", Icons.Default.Description)
    object Income : Screen("income", "Einnahmen", Icons.Default.Payments)
    object MonthlyOverview : Screen("overview", "Übersicht", Icons.Default.Assessment)
    object PrintPreview : Screen("print_preview", "Druckvorschau", Icons.Default.Print)
}

class MainActivity : ComponentActivity() {

    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeKey by viewModel.settingsManager.themeKey.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.settingsManager.isDarkMode.collectAsStateWithLifecycle()
            val autoBackupEnabled by viewModel.settingsManager.autoBackupEnabled.collectAsStateWithLifecycle()
            val autoBackupUri by viewModel.settingsManager.autoBackupUri.collectAsStateWithLifecycle()
            val lastBackupTime by viewModel.settingsManager.lastBackupTime.collectAsStateWithLifecycle()
            val backupStatus by viewModel.settingsManager.backupStatus.collectAsStateWithLifecycle()

            val useDarkTheme = isDarkMode ?: isSystemInDarkTheme()
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            val barContentColor = if (useDarkTheme) Color.White else Color.Black

            @OptIn(ExperimentalMaterial3Api::class)
            HaushaltsbuchTheme(
                themeKey = themeKey,
                darkTheme = useDarkTheme
            ) {
                val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
                val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()

                val fixedCosts by viewModel.fixedCosts.collectAsStateWithLifecycle()
                val recurringExpenses by viewModel.recurringExpenses.collectAsStateWithLifecycle()
                val invoices by viewModel.invoices.collectAsStateWithLifecycle()
                val incomes by viewModel.incomes.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()

                val activity = LocalContext.current as? Activity
                var currentScreen by remember { mutableStateOf<Screen>(Screen.MonthlyOverview) }
                var showCategoryDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = currentScreen != Screen.MonthlyOverview) {
                    currentScreen = Screen.MonthlyOverview
                }

                Scaffold(
                    topBar = {
                        if (currentScreen != Screen.PrintPreview) {
                            Column {
                                CenterAlignedTopAppBar(
                                    navigationIcon = {
                                        Box(modifier = Modifier.padding(start = 12.dp)) {
                                            AsyncImage(
                                                model = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTPYfEJ3M3SS0WV5torrO4t-9V7X-4PNqvAxUWy-pHuODbDiJgRRPvcAsRS&s=10",
                                                contentDescription = "Haushaltsbuch Logo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    },
                                    title = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Haushaltsbuch",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${PrintHelper.getGermanMonthName(selectedMonth)} $selectedYear",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { showSettingsDialog = true },
                                            modifier = Modifier.testTag("backup_top_bar")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Einstellungen, Design & Sicherung",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { showCategoryDialog = true },
                                            modifier = Modifier.testTag("manage_categories_top_bar")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = "Kategorien verwalten",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { showExitDialog = true },
                                            modifier = Modifier.testTag("exit_app_top_bar")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PowerSettingsNew,
                                                contentDescription = "App beenden",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                                        navigationIconContentColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (currentScreen != Screen.PrintPreview) {
                            Column {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                NavigationBar(
                                    modifier = Modifier.testTag("main_navigation_bar"),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                val navItems = listOf(
                                    Screen.MonthlyOverview,
                                    Screen.FixedCosts,
                                    Screen.RecurringExpenses,
                                    Screen.Invoices,
                                    Screen.Income
                                )

                                navItems.forEach { screen ->
                                    val isSelected = currentScreen == screen
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { currentScreen = screen },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = screen.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.testTag("nav_${screen.route}")
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                Screen.MonthlyOverview -> MonthlyOverviewScreen(
                                    selectedMonth = selectedMonth,
                                    selectedYear = selectedYear,
                                    incomes = incomes,
                                    fixedCosts = fixedCosts,
                                    recurringExpenses = recurringExpenses,
                                    invoices = invoices,
                                    categories = categories.map { it.name },
                                    onPreviousMonth = { viewModel.navigateMonth(-1) },
                                    onNextMonth = { viewModel.navigateMonth(1) },
                                    onMonthSelected = { m, y -> viewModel.setSelectedMonthAndYear(m, y) },
                                    onOpenPrintPreview = { currentScreen = Screen.PrintPreview },
                                    onSaveFixedCost = { viewModel.saveFixedCost(it) },
                                    onDeleteFixedCost = { viewModel.deleteFixedCost(it) },
                                    onManageCategories = { showCategoryDialog = true },
                                    onQuickAddCategory = { name -> viewModel.addCategory(name, "FIXED_COST") }
                                )

                                Screen.FixedCosts -> FixedCostsScreen(
                                    fixedCosts = fixedCosts,
                                    categories = categories,
                                    onSaveCost = { viewModel.saveFixedCost(it) },
                                    onToggleActive = { viewModel.toggleFixedCostActive(it) },
                                    onDeleteCost = { viewModel.deleteFixedCost(it) },
                                    onAddCategory = { name, type -> viewModel.addCategory(name, type) },
                                    onManageCategories = { showCategoryDialog = true }
                                )

                                Screen.RecurringExpenses -> RecurringExpensesScreen(
                                    recurringExpenses = recurringExpenses,
                                    categories = categories,
                                    selectedMonth = selectedMonth,
                                    selectedYear = selectedYear,
                                    onPreviousMonth = { viewModel.navigateMonth(-1) },
                                    onNextMonth = { viewModel.navigateMonth(1) },
                                    onMonthSelected = { m, y -> viewModel.setSelectedMonthAndYear(m, y) },
                                    onSaveExpense = { viewModel.saveRecurringExpense(it) },
                                    onToggleActive = { viewModel.toggleRecurringExpenseActive(it) },
                                    onDeleteExpense = { viewModel.deleteRecurringExpense(it) },
                                    onAddCategory = { name, type -> viewModel.addCategory(name, type) },
                                    onManageCategories = { showCategoryDialog = true }
                                )

                                Screen.Invoices -> InvoicesScreen(
                                    invoices = invoices,
                                    categories = categories,
                                    onSaveInvoice = { viewModel.saveInvoice(it) },
                                    onTogglePaid = { viewModel.toggleInvoicePaid(it) },
                                    onDeleteInvoice = { viewModel.deleteInvoice(it) },
                                    onAddCategory = { name, type -> viewModel.addCategory(name, type) },
                                    onManageCategories = { showCategoryDialog = true }
                                )

                                Screen.Income -> IncomeScreen(
                                    incomes = incomes,
                                    categories = categories,
                                    selectedMonth = selectedMonth,
                                    selectedYear = selectedYear,
                                    settingsManager = viewModel.settingsManager,
                                    onPreviousMonth = { viewModel.navigateMonth(-1) },
                                    onNextMonth = { viewModel.navigateMonth(1) },
                                    onMonthSelected = { m, y -> viewModel.setSelectedMonthAndYear(m, y) },
                                    onSaveIncome = { viewModel.saveIncome(it) },
                                    onToggleActive = { viewModel.toggleIncomeActive(it) },
                                    onDeleteIncome = { viewModel.deleteIncome(it) },
                                    onAddCategory = { name, type -> viewModel.addCategory(name, type) },
                                    onManageCategories = { showCategoryDialog = true }
                                )

                                Screen.PrintPreview -> PrintPreviewScreen(
                                    monthName = PrintHelper.getGermanMonthName(selectedMonth),
                                    year = selectedYear,
                                    incomes = incomes,
                                    fixedCosts = fixedCosts,
                                    recurringExpenses = recurringExpenses,
                                    invoices = invoices,
                                    onBack = { currentScreen = Screen.MonthlyOverview }
                                )
                            }
                        }

                        if (showCategoryDialog) {
                            CategoryManagementDialog(
                                categories = categories,
                                onDismiss = { showCategoryDialog = false },
                                onAddCategory = { name, type -> viewModel.addCategory(name, type) },
                                onUpdateCategory = { cat, newName -> viewModel.updateCategory(cat, newName) },
                                onDeleteCategory = { cat -> viewModel.deleteCategory(cat) }
                            )
                        }

                        if (showSettingsDialog) {
                            SettingsAndBackupDialog(
                                settingsManager = viewModel.settingsManager,
                                currentThemeKey = themeKey,
                                isDarkMode = isDarkMode,
                                autoBackupEnabled = autoBackupEnabled,
                                autoBackupUri = autoBackupUri,
                                lastBackupTime = lastBackupTime,
                                backupStatus = backupStatus,
                                onExportManualJson = { viewModel.generateBackupJson() },
                                onImportJson = { json ->
                                    viewModel.restoreBackup(json) { success, msg ->
                                        if (success) {
                                            android.widget.Toast.makeText(this@MainActivity, msg ?: "Sicherung erfolgreich wiederhergestellt!", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            android.widget.Toast.makeText(this@MainActivity, "Fehler beim Importieren: $msg", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onDismiss = { showSettingsDialog = false }
                            )
                        }

                        if (showExitDialog) {
                            AlertDialog(
                                onDismissRequest = { showExitDialog = false },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                title = { Text("App beenden?") },
                                text = {
                                    Column {
                                        Text("Möchten Sie das Haushaltsbuch wirklich beenden?")
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Alle Daten wurden automatisch gesichert.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showExitDialog = false
                                            activity?.finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Beenden")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExitDialog = false }) {
                                        Text("Abbrechen")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
