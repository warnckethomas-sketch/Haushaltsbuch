package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Haushaltsbuch", appName)
  }

  @Test
  fun `care payers serialization and deserialization in android runtime`() {
    val payers = listOf(
        com.example.data.entities.CarePayer(name = "Beihilfe", percentage = 70.0, calculatedAmount = 429.80),
        com.example.data.entities.CarePayer(name = "Privatkasse", percentage = 30.0, calculatedAmount = 184.20)
    )
    val json = com.example.data.entities.serializeCarePayers(payers)

    val income = com.example.data.entities.Income(
        title = "Pflegegeld Mutter",
        amount = 614.0,
        category = "Pflegegeld",
        isPflegegeld = true,
        carePayersJson = json
    )

    val deserialized = income.getCarePayers()
    assertEquals(2, deserialized.size)
    assertEquals("Beihilfe", deserialized[0].name)
    assertEquals(70.0, deserialized[0].percentage, 0.001)
    assertEquals(429.80, deserialized[0].calculatedAmount, 0.001)
    assertEquals("Privatkasse", deserialized[1].name)
    assertEquals(30.0, deserialized[1].percentage, 0.001)
    assertEquals(184.20, deserialized[1].calculatedAmount, 0.001)
  }

  @Test
  fun `payer subtotals aggregation correctly sums partial amounts per payer`() {
    val payers1 = listOf(
        com.example.data.entities.CarePayer(name = "Beihilfe", percentage = 70.0, calculatedAmount = 429.80),
        com.example.data.entities.CarePayer(name = "Privatkasse", percentage = 30.0, calculatedAmount = 184.20)
    )
    val inc1 = com.example.data.entities.Income(
        title = "Pflegegeld Mutter",
        amount = 614.0,
        category = "Pflegegeld",
        isPflegegeld = true,
        carePayersJson = com.example.data.entities.serializeCarePayers(payers1)
    )

    val payers2 = listOf(
        com.example.data.entities.CarePayer(name = "Beihilfe", percentage = 50.0, calculatedAmount = 166.0),
        com.example.data.entities.CarePayer(name = "AOK", percentage = 50.0, calculatedAmount = 166.0)
    )
    val inc2 = com.example.data.entities.Income(
        title = "Pflegegeld Vater",
        amount = 332.0,
        category = "Pflegegeld",
        isPflegegeld = true,
        carePayersJson = com.example.data.entities.serializeCarePayers(payers2)
    )

    val incomes = listOf(inc1, inc2)
    val map = linkedMapOf<String, Double>()
    incomes.forEach { inc ->
        inc.getCarePayers().forEach { p ->
            map[p.name] = (map[p.name] ?: 0.0) + p.calculatedAmount
        }
    }

    assertEquals(3, map.size)
    assertEquals(595.80, map["Beihilfe"] ?: 0.0, 0.001)
    assertEquals(184.20, map["Privatkasse"] ?: 0.0, 0.001)
    assertEquals(166.0, map["AOK"] ?: 0.0, 0.001)
  }
}
