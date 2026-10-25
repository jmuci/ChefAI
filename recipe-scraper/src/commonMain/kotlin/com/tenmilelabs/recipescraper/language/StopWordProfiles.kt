package com.tenmilelabs.recipescraper.language

/**
 * Per-language word lists for [scoreText]: common function words plus the vocabulary recipes
 * lean on (units, staples, cooking verbs). Recipe text is short and formulaic, so a few dozen
 * words per language separate them far more reliably than they would on general prose.
 *
 * Words shared between languages (`de`, `la`, `con`, `sal`) are deliberately kept in each list
 * they belong to: they add to every candidate equally and the distinctive words decide.
 * Single letters are kept only where they are that language's everyday conjunction or article
 * (`y`, `e`, `o`); `a`, a preposition or article in four of these languages, is left out, as are
 * unit abbreviations written identically everywhere (`g`, `ml`).
 *
 * Six languages are profiled, not two, even though only English and Spanish get language-aware
 * ingredient parsing: without a Portuguese or Italian profile, a Portuguese page would be
 * confidently scored as Spanish.
 */
internal val STOP_WORD_PROFILES: Map<String, Set<String>> = mapOf(
    "en" to setOf(
        "the", "and", "with", "of", "to", "for", "or", "until", "into", "from", "over", "then",
        "about", "each", "together", "is", "it", "minutes", "minute", "hour", "hours", "cup",
        "cups", "tablespoon", "tablespoons", "teaspoon", "teaspoons", "add", "stir", "heat",
        "bake", "oven", "preheat", "serve", "cook", "mix", "pan", "salt", "pepper", "sugar",
        "flour", "butter", "water", "oil", "garlic", "onion", "eggs", "milk", "chopped", "large",
        "small", "taste",
    ),
    "es" to setOf(
        "el", "la", "los", "las", "y", "o", "de", "del", "con", "en", "un", "una", "por", "para",
        "hasta", "al", "se", "que", "durante", "minutos", "hora", "horas", "taza", "tazas",
        "cucharada", "cucharadas", "cucharadita", "cucharaditas", "añadir", "añade", "agregar",
        "agrega", "mezclar", "mezcla", "cocinar", "cortar", "horno", "fuego", "sartén", "sal",
        "pimienta", "azúcar", "harina", "mantequilla", "agua", "aceite", "ajo", "cebolla",
        "huevos", "leche", "picado", "picada", "gusto",
    ),
    "pt" to setOf(
        "o", "os", "as", "e", "de", "do", "da", "dos", "das", "com", "em", "no", "na", "um",
        "uma", "ao", "até", "por", "para", "que", "se", "durante", "minutos", "hora", "horas",
        "xícara", "xícaras", "colher", "colheres", "sopa", "chá", "adicione", "misture",
        "cozinhe", "forno", "fogo", "frigideira", "sal", "pimenta", "açúcar", "farinha",
        "manteiga", "água", "azeite", "alho", "cebola", "ovos", "leite", "picado", "gosto", "não",
    ),
    "fr" to setOf(
        "le", "la", "les", "et", "de", "du", "des", "un", "une", "au", "aux", "avec", "dans",
        "pour", "sur", "puis", "pendant", "jusqu", "est", "minutes", "heure", "heures", "cuillère",
        "cuillères", "soupe", "café", "ajouter", "ajoutez", "mélanger", "mélangez", "cuire",
        "faire", "four", "feu", "poêle", "sel", "poivre", "sucre", "farine", "beurre", "eau",
        "huile", "ail", "oignon", "œufs", "oeufs", "lait", "haché", "hachée",
    ),
    "it" to setOf(
        "il", "lo", "gli", "le", "e", "di", "del", "della", "dei", "delle", "con", "nel", "nella",
        "un", "una", "per", "fino", "poi", "che", "minuti", "ora", "ore", "cucchiaio", "cucchiai",
        "cucchiaino", "aggiungere", "aggiungete", "mescolare", "cuocere", "forno", "fuoco",
        "padella", "sale", "pepe", "zucchero", "farina", "burro", "acqua", "olio", "aglio",
        "cipolla", "uova", "latte", "tritato", "quanto", "basta",
    ),
    "de" to setOf(
        "der", "die", "das", "und", "mit", "von", "den", "dem", "ein", "eine", "einen", "zu",
        "für", "bis", "auf", "im", "ist", "dann", "etwa", "nicht", "minuten", "stunde", "stunden",
        "esslöffel", "teelöffel", "hinzufügen", "verrühren", "kochen", "backen", "ofen",
        "backofen", "pfanne", "salz", "pfeffer", "zucker", "mehl", "wasser", "öl", "knoblauch",
        "zwiebel", "zwiebeln", "eier", "milch", "gehackt", "prise",
    ),
)
