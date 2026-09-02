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
}
