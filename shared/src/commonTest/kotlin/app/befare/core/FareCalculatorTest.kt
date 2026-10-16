package app.befare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FareCalculatorTest {

    private fun band(id: String, base: Long) =
        FuelBand(id, id, minimumFare = base, fares = (1..20).map { km -> base + 200L * maxOf(0, km - 2) })

    private val calc = FareCalculator(
        FareBundle(
            version = "test", currency = "PHP",
            sources = listOf(FareSource("src", "City", "City Ordinance No. 1185")),
            modes = listOf(
                FareMode(
                    "tricycle",
                    rules = listOf(
                        FareRule(
                            id = "r", type = "distance_table_by_fuel_band", label = "t", sourceId = "src",
                            kmRounding = "ceil", maxKm = 20,
                            bands = listOf(band("regular", 1200), band("gas-70-80", 1400)),
                        )
                    ),
                    discounts = listOf(Discount("d", "Senior / PWD / Student", "percent", 20, "src")),
                )
            ),
        )
    )

    private fun quote(m: Double, band: String = "regular") =
        assertIs<FareResult.Ok>(calc.quote(m, band)).quote

    @Test fun shortTripIsMinimumFare() = assertEquals(1200, quote(300.0).fareCentavos)
    @Test fun exactlyTwoKm() = assertEquals(1200, quote(2000.0).fareCentavos)
    @Test fun justOverTwoKmRoundsUp() = assertEquals(1400, quote(2001.0).let { it.fareCentavos })
    @Test fun fiveThreeKmChargedAsSix() = assertEquals(6, quote(5300.0).chargedKm)
    @Test fun bandApplies() = assertEquals(2200, quote(5300.0, "gas-70-80").fareCentavos)
    @Test fun discountIsTwentyPercent() = assertEquals(1760L, quote(5300.0, "gas-70-80").discountedCentavos)
    @Test fun rowCoversAllBands() = assertEquals(listOf(2000L, 2200L), quote(5300.0).row.map { it.centavos })
    @Test fun overTable() = assertIs<FareResult.OverTable>(calc.quote(20_001.0, "regular")).let { }
    @Test fun pesoFormat() = assertEquals("₱17.60", formatPeso(1760))
}
