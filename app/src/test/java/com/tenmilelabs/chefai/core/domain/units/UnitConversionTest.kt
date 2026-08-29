package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem.AS_WRITTEN
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem.IMPERIAL
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem.METRIC
import com.tenmilelabs.chefai.core.util.QuantityFormat
import org.junit.Test

class UnitConversionTest {

    private fun convert(
        quantity: Double,
        unit: String,
        name: String = "something",
        target: MeasurementSystem,
    ): ConvertedAmount = UnitConversion.convert(quantity, unit, name, target)

    /** What the user actually reads, so the tests assert on the rendered line, not on a Double. */
    private fun label(amount: ConvertedAmount): String =
        "${QuantityFormat.cooking(amount.quantity, amount.unit)} ${amount.unit}".trim()

    // region as-written

    @Test
    fun `as written - returns the recipe's own numbers untouched`() {
        val amount = convert(2.0, "cups", "all-purpose flour", AS_WRITTEN)

        assertThat(amount.quantity).isEqualTo(2.0)
        assertThat(amount.unit).isEqualTo("cups")
        assertThat(amount.isApproximate).isFalse()
    }

    // endregion

    // region units that must never convert

    @Test
    fun `counting units pass through in every mode`() {
        val countingUnits = listOf("clove", "cloves", "can", "jar", "slice", "stick", "sprig",
            "bunch", "head", "handful", "piece", "sheet", "pinch", "dash", "package")

        for (unit in countingUnits) {
            for (target in listOf(METRIC, IMPERIAL)) {
                val amount = convert(2.0, unit, "garlic", target)
                assertThat(amount.unit).isEqualTo(unit)
                assertThat(amount.quantity).isEqualTo(2.0)
            }
        }
    }

    @Test
    fun `the scraper's placeholder unit and a blank unit pass through`() {
        // ScrapedRecipeMapper writes the literal "unit" when a line carried no unit at all.
        assertThat(convert(1.0, "unit", "onion", METRIC).unit).isEqualTo("unit")
        assertThat(convert(1.0, "", "onion", METRIC).unit).isEqualTo("")
        assertThat(convert(1.0, "punnet", "strawberries", METRIC).unit).isEqualTo("punnet")
    }

    @Test
    fun `an amount already in the target system is left as written`() {
        val metric = convert(300.0, "g", "chickpeas", METRIC)
        assertThat(metric.unit).isEqualTo("g")
        assertThat(metric.quantity).isEqualTo(300.0)

        val imperial = convert(2.0, "cups", "rice", IMPERIAL)
        assertThat(imperial.unit).isEqualTo("cups")
        assertThat(imperial.quantity).isEqualTo(2.0)
    }

    @Test
    fun `a zero or negative amount is left alone rather than converted`() {
        assertThat(convert(0.0, "cup", "flour", METRIC).unit).isEqualTo("cup")
        assertThat(convert(-1.0, "oz", "butter", METRIC).unit).isEqualTo("oz")
    }

    // endregion

    // region imperial to metric

    @Test
    fun `to metric - mass converts to grams and promotes to kilograms`() {
        assertThat(label(convert(1.0, "oz", target = METRIC))).isEqualTo("28 g")
        assertThat(label(convert(8.0, "oz", target = METRIC))).isEqualTo("225 g")
        assertThat(label(convert(1.0, "lb", target = METRIC))).isEqualTo("455 g")
        assertThat(label(convert(3.0, "lb", target = METRIC))).isEqualTo("1.36 kg")
    }

    @Test
    fun `to metric - volume converts to the millilitre figures the charts print`() {
        // The whole point of the rounding grid: a cup is 236.588 ml and nobody writes that down.
        assertThat(label(convert(1.0, "cup", target = METRIC))).isEqualTo("240 ml")
        assertThat(label(convert(0.5, "cup", target = METRIC))).isEqualTo("120 ml")
        assertThat(label(convert(0.25, "cup", target = METRIC))).isEqualTo("60 ml")
        assertThat(label(convert(1.0, "tbsp", target = METRIC))).isEqualTo("15 ml")
        assertThat(label(convert(1.0, "tsp", target = METRIC))).isEqualTo("5 ml")
        assertThat(label(convert(0.5, "tsp", target = METRIC))).isEqualTo("2.5 ml")
    }

    @Test
    fun `to metric - volume promotes to litres`() {
        assertThat(label(convert(2.0, "quart", target = METRIC))).isEqualTo("1.89 l")
    }

    @Test
    fun `to metric - a known ingredient crosses from cups to grams`() {
        val flour = convert(1.0, "cup", "all-purpose flour", METRIC)

        assertThat(label(flour)).isEqualTo("120 g")
        assertThat(flour.isApproximate).isTrue()
    }

    @Test
    fun `to metric - an unknown ingredient stays a volume`() {
        val stock = convert(1.0, "cup", "beef stock", METRIC)

        assertThat(label(stock)).isEqualTo("240 ml")
        assertThat(stock.isApproximate).isFalse()
    }

    @Test
    fun `to metric - a prepared ingredient stays a volume, however well known`() {
        // A cup of chopped walnuts weighs whatever it was pressed to weigh.
        val chopped = convert(1.0, "cup", "chopped walnuts", METRIC)

        assertThat(chopped.unit).isEqualTo("ml")
        assertThat(chopped.isApproximate).isFalse()
    }

    // endregion

    // region metric to imperial

    @Test
    fun `to imperial - volume converts to cups, spoons and fractions`() {
        assertThat(label(convert(240.0, "ml", target = IMPERIAL))).isEqualTo("1 cup")
        assertThat(label(convert(120.0, "ml", target = IMPERIAL))).isEqualTo("½ cup")
        assertThat(label(convert(15.0, "ml", target = IMPERIAL))).isEqualTo("1 tbsp")
        assertThat(label(convert(5.0, "ml", target = IMPERIAL))).isEqualTo("1 tsp")
        assertThat(label(convert(1.0, "l", target = IMPERIAL))).isEqualTo("4¼ cup")
    }

    @Test
    fun `to imperial - mass converts to ounces and promotes to pounds`() {
        assertThat(label(convert(100.0, "g", target = IMPERIAL))).isEqualTo("3½ oz")
        assertThat(label(convert(500.0, "g", target = IMPERIAL))).isEqualTo("1⅛ lb")
        assertThat(label(convert(1.0, "kg", target = IMPERIAL))).isEqualTo("2¼ lb")
    }

    @Test
    fun `to imperial - a known ingredient crosses from grams to cups`() {
        val flour = convert(240.0, "g", "plain flour", IMPERIAL)

        assertThat(label(flour)).isEqualTo("2 cup")
        assertThat(flour.isApproximate).isTrue()
    }

    @Test
    fun `to imperial - an unknown ingredient stays a weight`() {
        val beef = convert(500.0, "g", "beef brisket", IMPERIAL)

        assertThat(beef.unit).isEqualTo("lb")
        assertThat(beef.isApproximate).isFalse()
    }

    // endregion

    // region rounding

    @Test
    fun `converted imperial amounts always land on a fraction a measuring cup can express`() {
        // Anything off the grid makes QuantityFormat give up and print "0.78 cup".
        for (millilitres in 20..1000 step 7) {
            val amount = convert(millilitres.toDouble(), "ml", target = IMPERIAL)
            assertThat(QuantityFormat.cooking(amount.quantity, amount.unit)).doesNotContain(".")
        }
    }

    @Test
    fun `rounding up to the next unit promotes rather than printing a full thousand`() {
        // 2.2 lb is 997.9 g, which rounds to 1000. Deciding the promotion on the raw value left
        // that reading "1000 g" in a list where everything else says kg.
        assertThat(label(convert(2.2, "lb", target = METRIC))).isEqualTo("1 kg")
        assertThat(label(convert(4.22, "cups", target = METRIC))).isEqualTo("1 l")
        assertThat(label(convert(452.0, "g", target = IMPERIAL))).isEqualTo("1 lb")

        // Values genuinely below the threshold still keep the smaller unit.
        assertThat(label(convert(2.0, "lb", target = METRIC))).isEqualTo("905 g")
        assertThat(label(convert(400.0, "g", target = IMPERIAL))).isEqualTo("14⅛ oz")
    }

    @Test
    fun `no converted amount ever prints a whole thousand of the smaller unit`() {
        for (pounds in 200..250) {
            val amount = convert(pounds / 100.0, "lb", target = METRIC)
            assertThat("${amount.quantity} ${amount.unit}").isNotEqualTo("1000.0 g")
        }
    }

    @Test
    fun `a real amount never rounds away to zero`() {
        // 1 g of saffron is 0.035 oz. Snapping that to the nearest measurable fraction gives zero,
        // which would tell the cook they need none of it — so the rounding stands down and lets
        // the true value through instead.
        val saffron = convert(1.0, "g", "saffron threads", IMPERIAL)
        assertThat(saffron.quantity).isGreaterThan(0.0)
        assertThat(label(saffron)).isEqualTo("0.04 oz")

        val trace = convert(0.05, "tsp", "vanilla extract", METRIC)
        assertThat(trace.quantity).isGreaterThan(0.0)

        // Across the whole range where the grids could swallow a value, nothing reaches zero.
        for (milligrams in 1..2000 step 7) {
            val amount = convert(milligrams / 1000.0, "g", "salt", IMPERIAL)
            assertThat(amount.quantity).isGreaterThan(0.0)
        }
        assertThat(UnitConversion.roundGrams(0.2)).isGreaterThan(0.0)
        assertThat(UnitConversion.roundMillilitres(0.1)).isGreaterThan(0.0)
        assertThat(UnitConversion.roundCookingFraction(0.01)).isGreaterThan(0.0)
    }

    @Test
    fun `converted metric amounts round to figures a person would write down`() {
        assertThat(UnitConversion.roundGrams(236.588)).isEqualTo(235.0)
        assertThat(UnitConversion.roundGrams(28.349)).isEqualTo(28.0)
        assertThat(UnitConversion.roundGrams(4.93)).isEqualTo(5.0)
        assertThat(UnitConversion.roundMillilitres(236.588)).isEqualTo(240.0)
        assertThat(UnitConversion.roundMillilitres(14.787)).isEqualTo(15.0)
        assertThat(UnitConversion.roundMillilitres(4.929)).isEqualTo(5.0)
    }

    // endregion
}
