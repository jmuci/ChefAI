package com.tenmilelabs.chefai.core.domain.units

/**
 * Recognises the preparation notes recipes hang on an ingredient — `garlic, peeled and crushed`,
 * `ajo picado` — so aggregation can see that they are still just garlic.
 *
 * Only ever applied to the *trailing* part of a name. A leading note is often part of the product
 * (`diced tomatoes` is a can, `tomatoes, diced` is a chore), so those are left alone. `ground` and
 * `molido` are left out for the same reason: `ground beef` is not beef with a note.
 *
 * English and Spanish only — the two languages the ingredient parser reads (ADR-016).
 */
object PreparationNotes {

    /** [name] without a trailing preparation note, or [name] itself when it has none (or is nothing but one). */
    fun stripFrom(name: String): String {
        val trimmed = name.trim()
        // "garlic, peeled and crushed" — everything after the first comma is the note.
        val comma = trimmed.indexOf(',')
        if (comma > 0 && isNote(words(trimmed.substring(comma + 1)))) return trimmed.substring(0, comma).trim()

        // "garlic peeled crushed" — the longest trailing run that reads as a note, keeping at least
        // one word of name.
        val tokens = trimmed.split(WHITESPACE).filter { it.isNotEmpty() }
        for (start in 1 until tokens.size) {
            if (isNote(tokens.subList(start, tokens.size).map(::bare))) {
                return tokens.subList(0, start).joinToString(" ").trimEnd(',')
            }
        }
        return trimmed
    }

    /**
     * Whether [tokens] read as a preparation note: only note vocabulary, and at least one real
     * preparation word (so `in oil` or `to taste` alone are not notes).
     */
    fun isNote(tokens: List<String>): Boolean {
        val lowered = tokens.map { it.lowercase() }
        return lowered.isNotEmpty() && lowered.all { it in VOCABULARY } && lowered.any { it in PREPARATIONS }
    }

    private fun words(text: String): List<String> =
        text.split(WHITESPACE).map(::bare).filter { it.isNotEmpty() }

    private fun bare(token: String): String = token.trim('(', ')', ',', ';', '.')

    private val WHITESPACE = Regex("\\s+")

    /** Spanish participles inflect for gender and number: `pelad` + o/a/os/as. */
    private fun participle(stem: String): List<String> = listOf("o", "a", "os", "as").map { stem + it }

    private val PREPARATIONS: Set<String> = buildSet {
        addAll(
            listOf(
                "peeled", "crushed", "minced", "chopped", "diced", "sliced", "grated", "shredded",
                "crumbled", "cubed", "mashed", "melted", "softened", "beaten", "whisked", "drained",
                "rinsed", "washed", "trimmed", "halved", "quartered", "julienned", "pitted", "seeded",
                "deseeded", "cored", "thawed", "sifted", "toasted", "zested",
            ),
        )
        listOf(
            "pelad", "machacad", "aplastad", "picad", "trocead", "cortad", "laminad", "rallad",
            "desmenuzad", "triturad", "derretid", "ablandad", "batid", "escurrid", "lavad", "limpi",
            "deshuesad", "descongelad", "tostad", "filetead", "rebanad", "partid",
        ).forEach { addAll(participle(it)) }
    }

    /** Preparation words plus the glue a note is built from ("finely chopped", "cortado en dados"). */
    private val VOCABULARY: Set<String> = PREPARATIONS + setOf(
        "and", "or", "finely", "coarsely", "roughly", "thinly", "freshly", "lightly", "well", "very",
        "into", "in", "cubes", "slices", "strips", "pieces", "chunks", "rounds",
        "y", "o", "e", "en", "dados", "rodajas", "trozos", "tiras", "cubos", "rodajitas", "finamente",
        "fino", "fina", "finos", "finas", "gruesos", "gruesas", "pequeños", "pequeñas",
    )
}
