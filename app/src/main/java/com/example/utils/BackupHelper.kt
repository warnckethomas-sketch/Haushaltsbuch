package com.example.utils

import com.example.data.entities.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object BackupHelper {

    fun createBackupJson(
        categories: List<Category>,
        fixedCosts: List<FixedCost>,
        recurringExpenses: List<RecurringExpense>,
        invoices: List<Invoice>,
        incomes: List<Income>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(Date()))

        // Categories
        val catArray = JSONArray()
        categories.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("type", c.type)
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // Fixed Costs
        val fixedArray = JSONArray()
        fixedCosts.forEach { f ->
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("title", f.title)
            obj.put("amount", f.amount)
            obj.put("category", f.category)
            obj.put("dueDayOfMonth", f.dueDayOfMonth)
            obj.put("notes", f.notes)
            obj.put("isActive", f.isActive)
            obj.put("isRecurring", f.isRecurring)
            obj.put("endYear", f.endYear ?: JSONObject.NULL)
            obj.put("endMonth", f.endMonth ?: JSONObject.NULL)
            obj.put("specificYear", f.specificYear ?: JSONObject.NULL)
            obj.put("specificMonth", f.specificMonth ?: JSONObject.NULL)
            obj.put("overriddenFixedCostId", f.overriddenFixedCostId ?: JSONObject.NULL)
            fixedArray.put(obj)
        }
        root.put("fixedCosts", fixedArray)

        // Recurring Expenses
        val recurringArray = JSONArray()
        recurringExpenses.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("title", r.title)
            obj.put("amount", r.amount)
            obj.put("category", r.category)
            obj.put("notes", r.notes)
            obj.put("isActive", r.isActive)
            obj.put("intervalMonths", r.intervalMonths)
            obj.put("startMonth", r.startMonth)
            obj.put("startYear", r.startYear)
            obj.put("endMonth", r.endMonth ?: JSONObject.NULL)
            obj.put("endYear", r.endYear ?: JSONObject.NULL)
            recurringArray.put(obj)
        }
        root.put("recurringExpenses", recurringArray)

        // Invoices
        val invoiceArray = JSONArray()
        invoices.forEach { inv ->
            val obj = JSONObject()
            obj.put("id", inv.id)
            obj.put("title", inv.title)
            obj.put("amount", inv.amount)
            obj.put("category", inv.category)
            obj.put("notes", inv.notes)
            obj.put("isPaid", inv.isPaid)
            obj.put("dueYear", inv.dueYear)
            obj.put("dueMonth", inv.dueMonth)
            obj.put("dueDay", inv.dueDay)
            obj.put("paidYear", inv.paidYear ?: JSONObject.NULL)
            obj.put("paidMonth", inv.paidMonth ?: JSONObject.NULL)
            obj.put("paidDay", inv.paidDay ?: JSONObject.NULL)
            invoiceArray.put(obj)
        }
        root.put("invoices", invoiceArray)

        // Incomes
        val incomeArray = JSONArray()
        incomes.forEach { inc ->
            val obj = JSONObject()
            obj.put("id", inc.id)
            obj.put("title", inc.title)
            obj.put("amount", inc.amount)
            obj.put("isRecurring", inc.isRecurring)
            obj.put("specificYear", inc.specificYear ?: JSONObject.NULL)
            obj.put("specificMonth", inc.specificMonth ?: JSONObject.NULL)
            obj.put("category", inc.category)
            obj.put("notes", inc.notes)
            obj.put("isActive", inc.isActive)
            obj.put("overriddenIncomeId", inc.overriddenIncomeId ?: JSONObject.NULL)
            obj.put("isPflegegeld", inc.isPflegegeld)
            obj.put("sender", inc.sender)
            obj.put("pflegegrad", inc.pflegegrad ?: JSONObject.NULL)
            obj.put("pflegegeldBaseAmount", inc.pflegegeldBaseAmount)
            obj.put("pflegehilfsmittelAmount", inc.pflegehilfsmittelAmount)
            obj.put("percentageShare", inc.percentageShare)
            incomeArray.put(obj)
        }
        root.put("incomes", incomeArray)

        return root.toString(2)
    }

    data class ParsedBackupData(
        val categories: List<Category>,
        val fixedCosts: List<FixedCost>,
        val recurringExpenses: List<RecurringExpense>,
        val invoices: List<Invoice>,
        val incomes: List<Income>
    )

    fun parseBackupJson(jsonString: String): ParsedBackupData {
        val root = JSONObject(jsonString)

        val categories = mutableListOf<Category>()
        if (root.has("categories")) {
            val arr = root.getJSONArray("categories")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                categories.add(
                    Category(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        type = obj.getString("type")
                    )
                )
            }
        }

        val fixedCosts = mutableListOf<FixedCost>()
        if (root.has("fixedCosts")) {
            val arr = root.getJSONArray("fixedCosts")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                fixedCosts.add(
                    FixedCost(
                        id = obj.optInt("id", 0),
                        title = obj.getString("title"),
                        amount = obj.getDouble("amount"),
                        category = obj.getString("category"),
                        dueDayOfMonth = obj.optInt("dueDayOfMonth", 1),
                        notes = obj.optString("notes", ""),
                        isActive = obj.optBoolean("isActive", true),
                        isRecurring = obj.optBoolean("isRecurring", true),
                        endYear = if (obj.isNull("endYear")) null else obj.optInt("endYear"),
                        endMonth = if (obj.isNull("endMonth")) null else obj.optInt("endMonth"),
                        specificYear = if (obj.isNull("specificYear")) null else obj.optInt("specificYear"),
                        specificMonth = if (obj.isNull("specificMonth")) null else obj.optInt("specificMonth"),
                        overriddenFixedCostId = if (obj.isNull("overriddenFixedCostId")) null else obj.optInt("overriddenFixedCostId")
                    )
                )
            }
        }

        val recurringExpenses = mutableListOf<RecurringExpense>()
        if (root.has("recurringExpenses")) {
            val arr = root.getJSONArray("recurringExpenses")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                recurringExpenses.add(
                    RecurringExpense(
                        id = obj.optInt("id", 0),
                        title = obj.getString("title"),
                        amount = obj.getDouble("amount"),
                        category = obj.getString("category"),
                        notes = obj.optString("notes", ""),
                        isActive = obj.optBoolean("isActive", true),
                        intervalMonths = obj.optInt("intervalMonths", 1),
                        startMonth = obj.optInt("startMonth", 1),
                        startYear = obj.optInt("startYear", 2026),
                        endMonth = if (obj.isNull("endMonth")) null else obj.optInt("endMonth"),
                        endYear = if (obj.isNull("endYear")) null else obj.optInt("endYear")
                    )
                )
            }
        }

        val invoices = mutableListOf<Invoice>()
        if (root.has("invoices")) {
            val arr = root.getJSONArray("invoices")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                invoices.add(
                    Invoice(
                        id = obj.optInt("id", 0),
                        title = obj.getString("title"),
                        amount = obj.getDouble("amount"),
                        category = obj.getString("category"),
                        notes = obj.optString("notes", ""),
                        isPaid = obj.optBoolean("isPaid", false),
                        dueYear = obj.getInt("dueYear"),
                        dueMonth = obj.getInt("dueMonth"),
                        dueDay = obj.getInt("dueDay"),
                        paidYear = if (obj.isNull("paidYear")) null else obj.optInt("paidYear"),
                        paidMonth = if (obj.isNull("paidMonth")) null else obj.optInt("paidMonth"),
                        paidDay = if (obj.isNull("paidDay")) null else obj.optInt("paidDay")
                    )
                )
            }
        }

        val incomes = mutableListOf<Income>()
        if (root.has("incomes")) {
            val arr = root.getJSONArray("incomes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                incomes.add(
                    Income(
                        id = obj.optInt("id", 0),
                        title = obj.getString("title"),
                        amount = obj.getDouble("amount"),
                        isRecurring = obj.optBoolean("isRecurring", true),
                        specificYear = if (obj.isNull("specificYear")) null else obj.optInt("specificYear"),
                        specificMonth = if (obj.isNull("specificMonth")) null else obj.optInt("specificMonth"),
                        category = obj.getString("category"),
                        notes = obj.optString("notes", ""),
                        isActive = obj.optBoolean("isActive", true),
                        overriddenIncomeId = if (obj.isNull("overriddenIncomeId")) null else obj.optInt("overriddenIncomeId"),
                        isPflegegeld = obj.optBoolean("isPflegegeld", obj.optString("category") == "Pflegegeld"),
                        sender = obj.optString("sender", ""),
                        pflegegrad = if (obj.isNull("pflegegrad")) null else obj.optInt("pflegegrad"),
                        pflegegeldBaseAmount = obj.optDouble("pflegegeldBaseAmount", 0.0),
                        pflegehilfsmittelAmount = obj.optDouble("pflegehilfsmittelAmount", 0.0),
                        percentageShare = obj.optDouble("percentageShare", 100.0)
                    )
                )
            }
        }

        return ParsedBackupData(
            categories = categories,
            fixedCosts = fixedCosts,
            recurringExpenses = recurringExpenses,
            invoices = invoices,
            incomes = incomes
        )
    }
}
