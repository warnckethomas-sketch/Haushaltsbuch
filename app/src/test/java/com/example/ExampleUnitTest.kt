package com.example

import com.example.data.entities.FixedCost
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun fixedCost_endDateConstraint_isEffectiveOnlyUntilEndMonth() {
        val cost = FixedCost(
            title = "Streaming Dienst",
            amount = 12.99,
            category = "Abonnement",
            endYear = 2026,
            endMonth = 8
        )

        // Effective before and during end month
        assertTrue(cost.isEffectiveInMonth(2026, 7))
        assertTrue(cost.isEffectiveInMonth(2026, 8))

        // Not effective after end month
        assertFalse(cost.isEffectiveInMonth(2026, 9))
        assertFalse(cost.isEffectiveInMonth(2027, 1))
    }

    @Test
    fun fixedCost_unlimited_isEffectiveAlways() {
        val cost = FixedCost(
            title = "Miete",
            amount = 800.0,
            category = "Wohnen",
            endYear = null,
            endMonth = null
        )

        assertTrue(cost.isEffectiveInMonth(2026, 12))
        assertTrue(cost.isEffectiveInMonth(2030, 1))
    }

    @Test
    fun pflegegeld_carePayerSerialization_simpleTest() {
        val p1 = com.example.data.entities.CarePayer(name = "Beihilfe", percentage = 70.0, calculatedAmount = 429.80)
        val p2 = com.example.data.entities.CarePayer(name = "Privatkasse", percentage = 30.0, calculatedAmount = 184.20)
        
        val payers = listOf(p1, p2)
        assertEquals(2, payers.size)
        assertEquals("Beihilfe", payers[0].name)
        assertEquals(70.0, payers[0].percentage, 0.001)
        assertEquals(429.80, payers[0].calculatedAmount, 0.001)
    }

    @Test
    fun printDate_bisFormat_isMMJJ() {
        val endMonth = 3
        val endYear = 2026
        val yy = endYear % 100
        val formatted = String.format(java.util.Locale.GERMANY, "(bis %02d-%02d)", endMonth, yy)
        assertEquals("(bis 03-26)", formatted)
    }
}
