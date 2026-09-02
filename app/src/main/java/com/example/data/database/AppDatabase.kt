package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CategoryDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.IncomeDao
import com.example.data.dao.InvoiceDao
import com.example.data.dao.RecurringExpenseDao
import com.example.data.entities.Category
import com.example.data.entities.CategoryType
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(
    entities = [FixedCost::class, RecurringExpense::class, Invoice::class, Income::class, Category::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fixedCostDao(): FixedCostDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun incomeDao(): IncomeDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fixed_costs ADD COLUMN endYear INTEGER")
                db.execSQL("ALTER TABLE fixed_costs ADD COLUMN endMonth INTEGER")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haushaltsbuch_database"
                )
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(
                        database.fixedCostDao(),
                        database.recurringExpenseDao(),
                        database.invoiceDao(),
                        database.incomeDao(),
                        database.categoryDao()
                    )
                }
            }
        }

        suspend fun populateInitialData(
            fixedCostDao: FixedCostDao,
            recurringExpenseDao: RecurringExpenseDao,
            invoiceDao: InvoiceDao,
            incomeDao: IncomeDao,
            categoryDao: CategoryDao
        ) {
            // Seed default categories
            val defaultFixedCategories = listOf("Wohnen", "Energie", "Kommunikation", "Abonnement", "Gesundheit & Sport", "Kredit/Darlehen", "Sonstiges")
            defaultFixedCategories.forEach { categoryDao.insertCategory(Category(name = it, type = CategoryType.FIXED_COST)) }

            val defaultRecurringCategories = listOf("Versicherungen", "Abgaben", "Fahrzeug", "Haushalt", "Mitgliedschaft", "Sonstiges")
            defaultRecurringCategories.forEach { categoryDao.insertCategory(Category(name = it, type = CategoryType.RECURRING_EXPENSE)) }

            val defaultInvoiceCategories = listOf("Gesundheit", "Fahrzeug", "Haushalt", "Handwerker", "Reparatur", "Einkauf", "Dienstleistung", "Sonstiges")
            defaultInvoiceCategories.forEach { categoryDao.insertCategory(Category(name = it, type = CategoryType.INVOICE)) }

            val defaultIncomeCategories = listOf("Gehalt", "Pflegegeld", "Staatlich", "Nebeneinkunft", "Kapital", "Rückerstattung", "Geschenk", "Einmalig", "Sonstiges")
            defaultIncomeCategories.forEach { categoryDao.insertCategory(Category(name = it, type = CategoryType.INCOME)) }
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1

            // 1. Fixed costs
            fixedCostDao.insertFixedCost(FixedCost(title = "Wohnungsmiete (Kalt+NK)", amount = 890.00, category = "Wohnen", dueDayOfMonth = 1))
            fixedCostDao.insertFixedCost(FixedCost(title = "Strom & Gas Stadtwerke", amount = 145.00, category = "Energie", dueDayOfMonth = 1))
            fixedCostDao.insertFixedCost(FixedCost(title = "Glasfaser Internet 250", amount = 44.99, category = "Kommunikation", dueDayOfMonth = 1))
            fixedCostDao.insertFixedCost(FixedCost(title = "Streaming Abos (Netflix/Spotify)", amount = 28.98, category = "Abonnement", dueDayOfMonth = 1))
            fixedCostDao.insertFixedCost(FixedCost(title = "Fitnessstudio Premium", amount = 34.90, category = "Gesundheit & Sport", dueDayOfMonth = 1))

            // 2. Recurring expenses
            recurringExpenseDao.insertRecurringExpense(
                RecurringExpense(
                    title = "Rundfunkbeitrag (GEZ)",
                    amount = 55.08,
                    intervalMonths = 3,
                    startYear = currentYear,
                    startMonth = currentMonth,
                    dueDayOfMonth = 1,
                    category = "Abgaben",
                    notes = "Vierteljährlicher Beitrag"
                )
            )
            recurringExpenseDao.insertRecurringExpense(
                RecurringExpense(
                    title = "KFZ-Haftpflicht + Kasko",
                    amount = 230.00,
                    intervalMonths = 6,
                    startYear = currentYear,
                    startMonth = currentMonth,
                    dueDayOfMonth = 1,
                    category = "Versicherungen",
                    notes = "Halbjährliche Abbuchung"
                )
            )
            recurringExpenseDao.insertRecurringExpense(
                RecurringExpense(
                    title = "Privat-Haftpflichtversicherung",
                    amount = 88.50,
                    intervalMonths = 12,
                    startYear = currentYear,
                    startMonth = currentMonth,
                    dueDayOfMonth = 1,
                    category = "Versicherungen",
                    notes = "Jährliche Abbuchung"
                )
            )
            recurringExpenseDao.insertRecurringExpense(
                RecurringExpense(
                    title = "Hundesteuer (Gesplittet)",
                    amount = 50.00,
                    intervalMonths = 0,
                    startYear = currentYear,
                    startMonth = 1,
                    dueDayOfMonth = 1,
                    category = "Abgaben",
                    notes = "100 € Gesamtsumme gesplittet auf März und September",
                    isSplit = true,
                    customMonths = "3:50.0,9:50.0",
                    totalYearlyAmount = 100.0
                )
            )

            // 3. Invoices
            invoiceDao.insertInvoice(
                Invoice(
                    invoiceNumber = "RE-2026-1042",
                    title = "Prophylaxe & Zahnreinigung",
                    vendor = "Zahnarztpraxis Dr. Weigel",
                    amount = 85.00,
                    dueYear = currentYear,
                    dueMonth = currentMonth,
                    dueDay = 20,
                    isPaid = false,
                    category = "Gesundheit",
                    notes = "Einreichung bei Zusatzversicherung offen"
                )
            )
            invoiceDao.insertInvoice(
                Invoice(
                    invoiceNumber = "INV-88392",
                    title = "Jahresinspektion KFZ",
                    vendor = "Autohaus & Werkstatt Schmidt",
                    amount = 310.40,
                    dueYear = currentYear,
                    dueMonth = currentMonth,
                    dueDay = 28,
                    isPaid = false,
                    category = "Fahrzeug",
                    notes = "Filter & Ölwechsel durchgeführt"
                )
            )
            invoiceDao.insertInvoice(
                Invoice(
                    invoiceNumber = "RE-9901",
                    title = "Sanitär Reparatur Spülkasten",
                    vendor = "Müller Haustechnik",
                    amount = 115.00,
                    dueYear = currentYear,
                    dueMonth = currentMonth,
                    dueDay = 5,
                    isPaid = true,
                    paidYear = currentYear,
                    paidMonth = currentMonth,
                    paidDay = 4,
                    category = "Haushalt",
                    notes = "Per Überweisung beglichen"
                )
            )

            // 4. Incomes
            incomeDao.insertIncome(
                Income(
                    title = "Hauptgehalt / Lohn",
                    amount = 2950.00,
                    isRecurring = true,
                    category = "Gehalt",
                    notes = "Monatlicher Gehaltseingang"
                )
            )
            incomeDao.insertIncome(
                Income(
                    title = "Kindergeld",
                    amount = 250.00,
                    isRecurring = true,
                    category = "Staatlich",
                    notes = "Monatliche Auszahlung"
                )
            )
            incomeDao.insertIncome(
                Income(
                    title = "Rückerstattung Nebenkosten",
                    amount = 180.00,
                    isRecurring = false,
                    specificYear = currentYear,
                    specificMonth = currentMonth,
                    category = "Einmalig",
                    notes = "Guthaben Vermieter Abrechnung"
                )
            )
        }
    }
}
