package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.tenmilelabs.chefai.core.domain.units.PreparationNotes

/**
 * Turns an ingredient's name as a recipe wrote it into what the shopping list shows and groups by.
 *
 * Two separate jobs:
 * - [displayName] drops what is a *note* rather than part of the name — a parenthetical
 *   ("Perejil fresco (una ramita)"), a trailing serving note ("salt, to taste") and a trailing
 *   preparation note ("garlic, peeled and crushed"). Language-independent: a bracket is a note in
 *   any language. The recipe itself keeps its full text; this is read-time only.
 * - [key] additionally folds regional synonyms onto one spelling ("aubergine" / "egg plants" →
 *   "eggplant") so both recipes' amounts land on one line. Applied to the key only — the line
 *   still shows a name one of the recipes actually used.
 *
 * Pure and Android-free, like the rest of the shopping-list domain.
 */
internal object ShoppingItemNames {

    /** [raw] without parenthetical, serving or preparation notes; [raw] trimmed if that would leave nothing. */
    fun displayName(raw: String): String {
        val withoutBrackets = stripBracketed(raw).replace(WHITESPACE, " ").trim().trimEnd(',', ';', ':').trim()
        if (withoutBrackets.isEmpty()) return raw.trim()
        return PreparationNotes.stripFrom(stripServingNote(withoutBrackets))
    }

    /**
     * The grouping and tick key for an already-[displayName]d name: lowercased, whitespace
     * collapsed, synonyms folded. Names without a synonym keep exactly the key they always had, so
     * existing ticks in `shopping_list_checks` survive.
     */
    fun key(displayName: String): String {
        val words = displayName.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val out = ArrayList<String>(words.size)
        var index = 0
        while (index < words.size) {
            val match = (minOf(MAX_SYNONYM_WORDS, words.size - index) downTo 1).firstNotNullOfOrNull { length ->
                SYNONYMS[words.subList(index, index + length).joinToString(" ")]?.let { it to length }
            }
            if (match == null) {
                out += words[index]
                index++
            } else {
                out += match.first
                index += match.second
            }
        }
        return out.joinToString(" ")
    }

    /** Removes every `( … )` / `[ … ]` group, and an unclosed one through to the end. */
    private fun stripBracketed(raw: String): String {
        val out = StringBuilder(raw.length)
        var depth = 0
        for (char in raw) {
            when (char) {
                '(', '[' -> depth++
                ')', ']' -> if (depth > 0) depth-- else out.append(' ')
                else -> if (depth == 0) out.append(char)
            }
        }
        return out.toString()
    }

    /** "salt, to taste" → "salt"; "queso, al gusto" → "queso". Only after a comma, only these leads. */
    private fun stripServingNote(name: String): String {
        val comma = name.indexOf(',')
        if (comma <= 0) return name
        val tail = name.substring(comma + 1).trim().lowercase()
        return if (SERVING_NOTE_LEADS.any { tail == it || tail.startsWith("$it ") }) name.substring(0, comma).trim() else name
    }

    private val WHITESPACE = Regex("\\s+")

    /** How a trailing note after a comma begins: "to taste", "for garnish", "al gusto", "para decorar". */
    private val SERVING_NOTE_LEADS = listOf(
        "to", "for", "optional", "if", "plus", "as",
        "al", "a", "para", "opcional", "si",
    )

    /**
     * Regional and descriptive spellings of the same thing you buy, folded onto one key.
     *
     * Data, not logic: add a pair here and every list picks it up. Spellings are matched as whole
     * words anywhere in the lowercased name ("fresh coriander" → "fresh cilantro"), longest first,
     * and each also matches with an `s`/`es` plural. Keep it to things a shopper would treat as
     * interchangeable — "double cream" vs "heavy cream" differ in fat and stay apart.
     */
    private val SYNONYM_GROUPS: Map<String, List<String>> = mapOf(
        "eggplant" to listOf("aubergine", "egg plant"),
        "zucchini" to listOf("courgette"),
        "cilantro" to listOf("coriander", "coriander leaves", "coriander leaf"),
        "arugula" to listOf("rocket"),
        "scallion" to listOf("spring onion", "green onion"),
        "chickpea" to listOf("garbanzo", "garbanzo bean"),
        "bell pepper" to listOf("capsicum", "sweet pepper"),
        "beet" to listOf("beetroot"),
        "rutabaga" to listOf("swede"),
        "snow pea" to listOf("mangetout"),
        "shrimp" to listOf("prawn"),
        "cornstarch" to listOf("cornflour", "corn starch"),
        "powdered sugar" to listOf("icing sugar", "confectioners sugar"),
        "superfine sugar" to listOf("caster sugar", "castor sugar"),
        "baking soda" to listOf("bicarbonate of soda", "bicarb soda"),
        "plain flour" to listOf("all-purpose flour", "all purpose flour"),
        "ground beef" to listOf("minced beef", "beef mince"),
        "ground pork" to listOf("minced pork", "pork mince"),
        "olive oil" to listOf("extra virgin olive oil", "extra-virgin olive oil", "virgin olive oil", "evoo"),
        "aceite de oliva" to listOf("aceite de oliva virgen extra", "aceite de oliva virgen", "aove"),
    )

    private val SYNONYMS: Map<String, String> = buildMap {
        for ((canonical, spellings) in SYNONYM_GROUPS) {
            for (spelling in spellings + canonical) {
                put(spelling, canonical)
                put(spelling + "s", canonical)
                put(spelling + "es", canonical)
            }
        }
    }

    private val MAX_SYNONYM_WORDS: Int = SYNONYMS.keys.maxOf { it.split(' ').size }
}
