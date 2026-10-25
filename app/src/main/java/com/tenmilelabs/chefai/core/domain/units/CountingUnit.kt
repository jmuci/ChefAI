package com.tenmilelabs.chefai.core.domain.units

/**
 * A unit that counts things rather than measuring them — `clove`, `can`, `diente`, `lata`.
 *
 * [UnitNormalizer] deliberately returns `null` for these (they have no dimension to convert), which
 * used to leave them as raw strings: `clove` and `cloves` landed in different shopping-list buckets
 * and never added up. Each unit here has one [singular] and one [plural], and either spelling
 * resolves to the same instance, so the pair is both the merge key and the display form.
 *
 * Kept per language rather than mapped onto English: a Spanish recipe's `2 dientes` must still read
 * as Spanish after being summed. Spanish and English spellings never collide, so no language flag
 * is needed to look one up.
 */
data class CountingUnit(val singular: String, val plural: String)

object CountingUnits {

    /**
     * The counting unit [raw] names, or `null` when it isn't one.
     *
     * Tolerates a trailing preparation note, because catalog data does carry them in the unit
     * field (`clove peeled crushed`): the note is dropped, since it describes the ingredient and
     * not how many there are. Use [resolveExact] where the note must survive.
     */
    fun resolve(raw: String): CountingUnit? {
        val tokens = tokens(raw)
        val unit = tokens.firstOrNull()?.let { BY_SPELLING[it] } ?: return null
        val rest = tokens.drop(1)
        return unit.takeIf { rest.isEmpty() || PreparationNotes.isNote(rest) }
    }

    /** The counting unit [raw] is spelled as, with nothing after it. */
    fun resolveExact(raw: String): CountingUnit? = tokens(raw).singleOrNull()?.let { BY_SPELLING[it] }

    /** [unit] spelled for [amount]: plural above one (`1.5 cloves`), singular otherwise. */
    fun label(unit: CountingUnit, amount: Double): String =
        if (amount > 1.0 + EPSILON) unit.plural else unit.singular

    private const val EPSILON = 1e-9

    private fun tokens(raw: String): List<String> =
        raw.trim().trim('.', ',').lowercase().split(WHITESPACE).filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("[\\s,]+")

    private val BY_SPELLING: Map<String, CountingUnit> = buildMap {
        fun unit(singular: String, plural: String, vararg synonyms: String) {
            val unit = CountingUnit(singular, plural)
            put(singular, unit)
            put(plural, unit)
            synonyms.forEach { put(it, unit) }
        }

        // English
        unit("clove", "cloves")
        unit("can", "cans", "tin", "tins")
        unit("jar", "jars")
        unit("slice", "slices")
        unit("pinch", "pinches")
        unit("dash", "dashes")
        unit("sprig", "sprigs")
        unit("bunch", "bunches")
        unit("head", "heads")
        unit("handful", "handfuls")
        unit("piece", "pieces")
        unit("sheet", "sheets")
        unit("stick", "sticks")
        unit("stalk", "stalks")
        unit("leaf", "leaves")
        unit("package", "packages", "pkg", "pkgs", "pack", "packs")
        unit("bag", "bags")
        unit("bottle", "bottles")
        unit("box", "boxes")
        unit("fillet", "fillets")

        // Spanish
        unit("diente", "dientes")
        unit("lata", "latas")
        unit("bote", "botes")
        unit("frasco", "frascos")
        unit("rebanada", "rebanadas")
        unit("loncha", "lonchas")
        unit("rodaja", "rodajas")
        unit("pizca", "pizcas")
        unit("chorrito", "chorritos")
        unit("ramita", "ramitas")
        unit("rama", "ramas")
        unit("manojo", "manojos")
        unit("cabeza", "cabezas")
        unit("puñado", "puñados", "punado", "punados")
        unit("pieza", "piezas")
        unit("hoja", "hojas")
        unit("tallo", "tallos")
        unit("paquete", "paquetes")
        unit("sobre", "sobres")
        unit("bolsa", "bolsas")
        unit("botella", "botellas")
        unit("caja", "cajas")
        unit("filete", "filetes")
    }
}
