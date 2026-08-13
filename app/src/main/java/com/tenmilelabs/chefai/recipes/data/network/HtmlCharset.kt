package com.tenmilelabs.chefai.recipes.data.network

import java.nio.charset.Charset

/** How far into the document to look for a `<meta>` charset declaration. */
private const val SNIFF_LIMIT_BYTES = 4096

/**
 * Decodes the first [length] bytes of [bytes] as HTML text.
 *
 * Recipe sites are not uniformly UTF-8 — plenty of older European ones still publish
 * ISO-8859-1 — and decoding those as UTF-8 turns every accented character into a replacement
 * glyph. The encoding is resolved the way browsers do it:
 *
 * 1. the `charset` parameter of the `Content-Type` response header, when the server sent one;
 * 2. otherwise a `<meta>` declaration sniffed from the head of the document;
 * 3. otherwise UTF-8, which is right far more often than not.
 *
 * Never throws: a charset name the JVM doesn't know falls through to the next step.
 */
internal fun decodeHtml(bytes: ByteArray, length: Int, headerCharset: Charset?): String {
    val charset = headerCharset ?: sniffMetaCharset(bytes, length) ?: Charsets.UTF_8
    return String(bytes, 0, length, charset)
}

/**
 * Reads a `<meta>` charset declaration out of the head of the document.
 *
 * Decodes the sniffing window as ISO-8859-1 first, which maps every byte to exactly one character
 * and so can never fail — the declaration itself is always ASCII, so this reads it correctly
 * whatever the real encoding turns out to be.
 */
private fun sniffMetaCharset(bytes: ByteArray, length: Int): Charset? {
    val head = String(bytes, 0, minOf(length, SNIFF_LIMIT_BYTES), Charsets.ISO_8859_1)
    val name = META_CHARSET.find(head)?.groupValues?.get(1)
        ?: META_HTTP_EQUIV_CHARSET.find(head)?.groupValues?.get(1)
        ?: return null
    return runCatching { Charset.forName(name.trim().trim('"', '\'')) }.getOrNull()
}

/** HTML5 form: `<meta charset="iso-8859-1">`. */
private val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?([\w-]+)""", RegexOption.IGNORE_CASE)

/** Legacy form: `<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">`. */
private val META_HTTP_EQUIV_CHARSET = Regex(
    """<meta[^>]+http-equiv\s*=\s*["']?content-type["']?[^>]+content\s*=\s*["'][^"']*charset\s*=\s*([\w-]+)""",
    RegexOption.IGNORE_CASE,
)
