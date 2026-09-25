package com.example.ui.components

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.entities.FixedCost
import com.example.data.entities.Income
import com.example.data.entities.Invoice
import com.example.data.entities.RecurringExpense
import java.text.NumberFormat
import java.util.Locale

object PrintHelper {

    fun printMonthlyReport(
        context: Context,
        monthName: String,
        year: Int,
        incomes: List<Income>,
        fixedCosts: List<FixedCost>,
        recurringExpenses: List<RecurringExpense>,
        invoices: List<Invoice>
    ) {
        val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        val mNum = monthNumber(monthName)
        val activeIncomes = incomes.filter { it.isEffectiveInMonth(year, mNum, incomes) }
        val regularIncomes = activeIncomes.filter { !it.isCareAllowanceItem }
        val pflegegeldIncomes = activeIncomes.filter { it.isCareAllowanceItem }

        val activeFixed = fixedCosts.filter { it.isEffectiveInMonth(year, mNum, fixedCosts) }
            .sortedWith(compareBy({ if (it.category.isBlank()) "Sonstiges" else it.category }, { it.title }))
        val activeRecurring = recurringExpenses.filter { it.isDueInMonth(year, mNum) }
            .sortedWith(compareBy({ !it.isActive }, { it.getNextDueMonthDelta(mNum, year) }, { it.getFirstDueMonth() }, { it.title.lowercase() }))
        val activeInvoices = invoices.filter { it.isDueInMonth(year, mNum) }

        val totalIncome = regularIncomes.sumOf { it.amount }
        val totalPflegegeld = pflegegeldIncomes.sumOf { it.amount }
        val totalFixed = activeFixed.sumOf { it.amount }
        val totalRecurring = activeRecurring.sumOf { it.getAmountForMonth(year, mNum) }
        val totalInvoices = activeInvoices.sumOf { it.amount }
        val totalExpenses = totalFixed + totalRecurring + totalInvoices
        val netBalance = totalIncome - totalExpenses

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Monatsuebersicht_${monthName}_${year}")
                val jobName = "Monatsuebersicht_${monthName}_${year}"
                printManager?.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }
        }

        val htmlBuilder = StringBuilder()
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4 portrait; margin: 20mm 20mm 25mm 25mm; }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; padding: 0; }
                    body { font-family: Arial, Helvetica, sans-serif; padding-bottom: 40px; color: #1e293b; font-size: 8pt; line-height: 1.2; background: #ffffff; position: relative; }
                    
                    /* DIN 5008 Falz- & Lochmarken (Lochungsabstand 2,5 cm) */
                    .din-mark { position: absolute; left: -25mm; border-top: 1px solid #94a3b8; }
                    .falzmarke-1 { top: 85mm; width: 8mm; }
                    .lochmarke { top: 128.5mm; width: 12mm; border-top: 1px dashed #64748b; }
                    .falzmarke-2 { top: 191mm; width: 8mm; }
                    
                    /* DIN 5008 Header & Briefkopf (Oberer Rand 4,5 cm inkl. Kopfbereich) */
                    .din-header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #3b82f6; padding-bottom: 3px; margin-bottom: 8px; margin-top: 5mm; }
                    .din-sender { display: flex; align-items: center; gap: 6px; }
                    .din-logo { width: 30px; height: 30px; border-radius: 4px; object-fit: cover; }
                    .din-sender-text h1 { margin: 0; font-size: 12pt; color: #1e40af; font-weight: bold; line-height: 1.1; }
                    .din-sender-text p { margin: 1px 0 0; font-size: 7.5pt; color: #3b82f6; text-transform: uppercase; letter-spacing: 0.5px; }
                    
                    /* DIN 5008 Informationsblock */
                    .din-infoblock { font-size: 7.5pt; color: #334155; background: rgba(224, 242, 254, 0.35); border: 1px solid #bae6fd; border-radius: 4px; padding: 2px 6px; }
                    .din-infoblock table { border-collapse: collapse; width: auto; }
                    .din-infoblock td { padding: 0 3px; border: none; font-size: 7.5pt; }
                    .din-infoblock td.label { color: #475569; font-weight: bold; text-align: right; }
                    .din-infoblock td.val { font-weight: bold; color: #1e40af; text-align: left; }
                    
                    /* Betreffzeile nach DIN 5008 */
                    .din-subject { font-size: 9.8pt; font-weight: bold; color: #1e40af; margin-bottom: 5px; padding-bottom: 2px; border-bottom: 1px solid #bae6fd; }
                    
                    .kpi-container { display: flex; justify-content: space-around; background: rgba(224, 242, 254, 0.4); border: 1px solid #bae6fd; border-radius: 4px; padding: 4px 6px; margin-bottom: 14px; }
                    .kpi-box { text-align: center; }
                    .kpi-label { font-size: 7.5pt; text-transform: uppercase; color: #475569; font-weight: bold; }
                    .kpi-value { font-size: 9.5pt; font-weight: bold; margin-top: 1px; }
                    .positive { color: #16a34a; }
                    .negative { color: #dc2626; }
                    
                    section { margin-bottom: 4px; page-break-inside: avoid; }
                    h2 { font-size: 8.2pt; color: #1e40af; border-bottom: 1px solid #bae6fd; padding-bottom: 1px; margin: 2px 0 1px 0; text-transform: uppercase; letter-spacing: 0.5px; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { text-align: left; padding: 2px 4px; border-bottom: 1px solid #e2e8f0; font-size: 8pt; }
                    th { background-color: rgba(59, 130, 246, 0.18); color: #1e40af; font-weight: bold; }
                    tbody tr:nth-child(even) { background-color: rgba(224, 242, 254, 0.45); }
                    tbody tr:nth-child(odd) { background-color: #ffffff; }
                    .amount-col { text-align: right; font-weight: bold; }
                    .check-col { text-align: right; width: 50px; padding-right: 4px; }
                    .check-box { display: inline-block; width: 11px; height: 11px; border: 1.2px solid #64748b; border-radius: 2px; text-align: center; line-height: 9px; font-size: 7.5pt; font-weight: bold; color: #16a34a; background: #ffffff; }
                    .total-row td { font-weight: bold; background-color: rgba(186, 230, 253, 0.6) !important; border-top: 1.5px solid #3b82f6; border-bottom: 1.5px solid #3b82f6; color: #0f172a; font-size: 8.2pt; }
                    
                    .din-footer { position: fixed; bottom: 0; left: 0; width: 100%; background: #ffffff; border-top: 1px solid #cbd5e1; padding-top: 4px; font-size: 7.5pt; color: #475569; }
                    .footnote-text { margin-bottom: 3px; color: #1e293b; background: rgba(241, 245, 249, 0.85); padding: 3px 6px; border-radius: 3px; border-left: 2.5px solid #3b82f6; font-size: 7.5pt; }
                    .footer-bottom { display: flex; justify-content: space-between; align-items: center; color: #64748b; font-size: 7.5pt; }
                    .page-number { font-weight: bold; color: #1e40af; }
                </style>
            </head>
            <body>
                <!-- DIN 5008 Falz- & Lochmarken -->
                <div class="din-mark falzmarke-1" title="Falzmarke 1"></div>
                <div class="din-mark lochmarke" title="Lochmarke"></div>
                <div class="din-mark falzmarke-2" title="Falzmarke 2"></div>

                <div class="din-header">
                    <div class="din-sender">
                        <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTPYfEJ3M3SS0WV5torrO4t-9V7X-4PNqvAxUWy-pHuODbDiJgRRPvcAsRS&s=10" class="din-logo" alt="Logo">
                        <div class="din-sender-text">
                            <h1>HAUSHALTSBUCH</h1>
                            <p>Finanzdokumentation nach DIN 5008</p>
                        </div>
                    </div>
                    <div class="din-infoblock">
                        <table>
                            <tr><td class="label">Datum:</td><td class="val">${java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(java.util.Date())}</td></tr>
                            <tr><td class="label">Abrechnung:</td><td class="val">$monthName $year</td></tr>
                            <tr><td class="label">Norm:</td><td class="val">DIN 5008 (Form B)</td></tr>
                        </table>
                    </div>
                </div>
                
                <div class="din-subject">
                    Betreff: Monatsübersicht & Finanzaufstellung $monthName $year
                </div>
                
                <div class="kpi-container">
                    <div class="kpi-box">
                        <div class="kpi-label">Einnahmen</div>
                        <div class="kpi-value positive">${fmt.format(totalIncome)}</div>
                    </div>
                    <div class="kpi-box">
                        <div class="kpi-label">Ausgaben Gesamt</div>
                        <div class="kpi-value negative">${fmt.format(totalExpenses)}</div>
                    </div>
                    <div class="kpi-box">
                        <div class="kpi-label">Netto-Saldo</div>
                        <div class="kpi-value ${if (netBalance >= 0) "positive" else "negative"}">${fmt.format(netBalance)}</div>
                    </div>
                </div>
        """.trimIndent())

        // 1. Incomes Table (Regulär)
        htmlBuilder.append("""
            <section>
                <h2>1. Monatliche Einnahmen (Regulär)</h2>
                <table>
                    <thead>
                        <tr><th>Bezeichnung</th><th>Kategorie</th><th>Typ</th><th class="amount-col">Betrag</th></tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        if (regularIncomes.isEmpty()) {
            htmlBuilder.append("""<tr><td colspan="4" style="text-align:center; color:#94a3b8;">Keine regulären Einnahmen für diesen Monat erfasst.</td></tr>""")
        } else {
            for (inc in regularIncomes) {
                htmlBuilder.append("""
                    <tr>
                        <td>${inc.title}</td>
                        <td>${inc.category}</td>
                        <td>${if (inc.isRecurring) "Wiederkehrend" else "Einmalig"}</td>
                        <td class="amount-col positive">+${fmt.format(inc.amount)}</td>
                    </tr>
                """.trimIndent())
            }
        }
        htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3">Gesamte Reguläre Einnahmen</td>
                        <td class="amount-col positive">+${fmt.format(totalIncome)}</td>
                    </tr>
                    </tbody>
                </table>
            </section>
        """.trimIndent())

        // 1b. Pflegegeld Table (Separater Ausweis)
        htmlBuilder.append("""
            <section>
                <h2>1b. Pflegegeld<sup>1</sup></h2>
                <table>
                    <thead>
                        <tr><th>Begünstigter / Bezeichnung</th><th>Überweiser / Pflegekasse</th><th>Anteil (%) / Pflege</th><th class="amount-col">Auszahlungsbetrag</th></tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        if (pflegegeldIncomes.isEmpty()) {
            htmlBuilder.append("""<tr><td colspan="4" style="text-align:center; color:#94a3b8;">Kein Pflegegeld für diesen Monat erfasst.</td></tr>""")
        } else {
            for (inc in pflegegeldIncomes) {
                val pct = if (inc.percentageShare % 1.0 == 0.0) inc.percentageShare.toInt().toString() else inc.percentageShare.toString()
                val gradInfo = if (inc.pflegegrad != null) "PG ${inc.pflegegrad}" else "Pflege"
                val pgDetail = if (inc.pflegegeldBaseAmount > 0 || inc.pflegehilfsmittelAmount > 0) {
                    " [PG-Basis: ${fmt.format(inc.pflegegeldBaseAmount)}, Hilfsmittel: ${fmt.format(inc.pflegehilfsmittelAmount)}]"
                } else ""
                val detail = "$pct% von ${fmt.format(inc.calculatedCareTotalBase)} ($gradInfo)$pgDetail"
                htmlBuilder.append("""
                    <tr>
                        <td>${inc.title}</td>
                        <td>${if (inc.sender.isNotBlank()) inc.sender else "Keine Angabe"}</td>
                        <td>${detail}</td>
                        <td class="amount-col positive">+${fmt.format(inc.amount)}</td>
                    </tr>
                """.trimIndent())
            }
        }
        htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3">Gesamt Pflegegeld (separat)</td>
                        <td class="amount-col positive">+${fmt.format(totalPflegegeld)}</td>
                    </tr>
                    </tbody>
                </table>
            </section>
        """.trimIndent())

        // 2. Fixed Costs Table
        htmlBuilder.append("""
            <section>
                <h2>2. Monatliche Fixkosten</h2>
                <table>
                    <thead>
                        <tr><th>Bezeichnung</th><th>Kategorie</th><th>Fälligkeit</th><th class="amount-col">Betrag</th><th class="check-col">Gebucht</th></tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        if (activeFixed.isEmpty()) {
            htmlBuilder.append("""<tr><td colspan="5" style="text-align:center; color:#94a3b8;">Keine Fixkosten erfasst.</td></tr>""")
        } else {
            for (fc in activeFixed) {
                val dueStr = if (fc.endYear != null && fc.endMonth != null) {
                    val yy = fc.endYear % 100
                    String.format(Locale.GERMANY, "%d. des Monats (bis %02d-%02d)", fc.dueDayOfMonth, fc.endMonth, yy)
                } else {
                    "${fc.dueDayOfMonth}. des Monats"
                }
                htmlBuilder.append("""
                    <tr>
                        <td>${fc.title}</td>
                        <td>${fc.category}</td>
                        <td>$dueStr</td>
                        <td class="amount-col negative">-${fmt.format(fc.amount)}</td>
                        <td class="check-col"><span class="check-box">&nbsp;</span></td>
                    </tr>
                """.trimIndent())
            }
        }
        htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3">Gesamt Fixkosten</td>
                        <td class="amount-col negative">-${fmt.format(totalFixed)}</td>
                        <td class="check-col"></td>
                    </tr>
                    </tbody>
                </table>
            </section>
        """.trimIndent())

        // 3. Recurring Expenses Table
        htmlBuilder.append("""
            <section>
                <h2>3. Wiederkehrende Ausgaben (Fällig im $monthName)</h2>
                <table>
                    <thead>
                        <tr><th>Bezeichnung</th><th>Kategorie</th><th>Intervall</th><th class="amount-col">Betrag</th><th class="check-col">Gebucht</th></tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        if (activeRecurring.isEmpty()) {
            htmlBuilder.append("""<tr><td colspan="5" style="text-align:center; color:#94a3b8;">Keine wiederkehrenden Sonderausgaben fällig.</td></tr>""")
        } else {
            for (re in activeRecurring) {
                val intervalStr = if (re.endYear != null && re.endMonth != null) {
                    val yy = re.endYear % 100
                    String.format(Locale.GERMANY, "%s (bis %02d-%02d)", re.getIntervalText(), re.endMonth, yy)
                } else {
                    re.getIntervalText()
                }
                htmlBuilder.append("""
                    <tr>
                        <td>${re.title}</td>
                        <td>${re.category}</td>
                        <td>$intervalStr</td>
                        <td class="amount-col negative">-${fmt.format(re.getAmountForMonth(year, mNum))}</td>
                        <td class="check-col"><span class="check-box">&nbsp;</span></td>
                    </tr>
                """.trimIndent())
            }
        }
        htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3">Gesamt Wiederkehrend ($monthName)</td>
                        <td class="amount-col negative">-${fmt.format(totalRecurring)}</td>
                        <td class="check-col"></td>
                    </tr>
                    </tbody>
                </table>
            </section>
        """.trimIndent())

        // 4. Invoices Table
        htmlBuilder.append("""
            <section>
                <h2>4. Rechnungen (Fällig im $monthName)</h2>
                <table>
                    <thead>
                        <tr><th>Rechnungs-Nr.</th><th>Empfänger / Betreff</th><th>Fällig am</th><th>Status</th><th class="amount-col">Betrag</th><th class="check-col">Gebucht</th></tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        if (activeInvoices.isEmpty()) {
            htmlBuilder.append("""<tr><td colspan="6" style="text-align:center; color:#94a3b8;">Keine Rechnungen in diesem Monat.</td></tr>""")
        } else {
            for (inv in activeInvoices) {
                val statusText = if (inv.isPaid) "Bezahlt (${inv.getPaidDateFormatted() ?: ""})" else "Offen"
                val checkMark = if (inv.isPaid) "&#10003;" else "&nbsp;"
                htmlBuilder.append("""
                    <tr>
                        <td>${inv.invoiceNumber.ifBlank { "-" }}</td>
                        <td>${inv.title} (${inv.vendor})</td>
                        <td>${inv.getDueDateFormatted()}</td>
                        <td>$statusText</td>
                        <td class="amount-col negative">-${fmt.format(inv.amount)}</td>
                        <td class="check-col"><span class="check-box">$checkMark</span></td>
                    </tr>
                """.trimIndent())
            }
        }
        htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="4">Gesamt Rechnungen ($monthName)</td>
                        <td class="amount-col negative">-${fmt.format(totalInvoices)}</td>
                        <td class="check-col"></td>
                    </tr>
                    </tbody>
                </table>
            </section>
            
            <div class="din-footer">
                <div class="footnote-text">
                    <sup>1</sup> <strong>Gesamtsaldo inkl. Pflegegeld:</strong> ${fmt.format(netBalance + totalPflegegeld)} (Saldo: ${fmt.format(netBalance)} + Pflegegeld: ${fmt.format(totalPflegegeld)})
                </div>
                <div class="footer-bottom">
                    <span>Haushaltsbuch App &bull; DIN 5008 &bull; Erstellt am ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(java.util.Date())}</span>
                    <span class="page-number">Seite 1 von 1</span>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent())

        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
    }

    private fun monthNumber(monthName: String): Int = when (monthName.lowercase(Locale.GERMANY)) {
        "januar" -> 1
        "februar" -> 2
        "märz", "maerz" -> 3
        "april" -> 4
        "mai" -> 5
        "juni" -> 6
        "juli" -> 7
        "august" -> 8
        "september" -> 9
        "oktober" -> 10
        "november" -> 11
        "dezember" -> 12
        else -> 1
    }

    fun getGermanMonthName(month: Int): String = when (month) {
        1 -> "Januar"
        2 -> "Februar"
        3 -> "März"
        4 -> "April"
        5 -> "Mai"
        6 -> "Juni"
        7 -> "Juli"
        8 -> "August"
        9 -> "September"
        10 -> "Oktober"
        11 -> "November"
        12 -> "Dezember"
        else -> "Januar"
    }
}
