package com.tenmilelabs.chefai.recipes.data.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.Charset

class HtmlCharsetTest {

    private fun decode(html: String, encodedAs: Charset, headerCharset: Charset? = null): String {
        val bytes = html.toByteArray(encodedAs)
        return decodeHtml(bytes, bytes.size, headerCharset)
    }

    @Test
    fun `uses the charset from the response header`() {
        val decoded = decode("<html><body>cocido madrileño</body></html>", Charsets.ISO_8859_1, Charsets.ISO_8859_1)

        assertThat(decoded).contains("madrileño")
    }

    @Test
    fun `header charset wins over a contradicting meta declaration`() {
        val html = """<html><head><meta charset="utf-8"></head><body>garbanzos jamón</body></html>"""

        val decoded = decode(html, Charsets.ISO_8859_1, headerCharset = Charsets.ISO_8859_1)

        assertThat(decoded).contains("jamón")
    }

    @Test
    fun `sniffs the html5 meta charset when the header omits one`() {
        val html = """<html><head><meta charset="iso-8859-1"></head><body>puré de patatas</body></html>"""

        val decoded = decode(html, Charsets.ISO_8859_1, headerCharset = null)

        assertThat(decoded).contains("puré")
    }

    @Test
    fun `sniffs the legacy http-equiv meta charset`() {
        val html = """
            <html><head>
            <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
            </head><body>añejo</body></html>
        """.trimIndent()

        val decoded = decode(html, Charsets.ISO_8859_1, headerCharset = null)

        assertThat(decoded).contains("añejo")
    }

    @Test
    fun `defaults to utf-8 when nothing declares a charset`() {
        val html = "<html><body>crème brûlée</body></html>"

        val decoded = decode(html, Charsets.UTF_8, headerCharset = null)

        assertThat(decoded).contains("crème brûlée")
    }

    @Test
    fun `falls back to utf-8 when the declared charset name is unknown`() {
        val html = """<html><head><meta charset="definitely-not-a-charset"></head><body>café</body></html>"""

        val decoded = decode(html, Charsets.UTF_8, headerCharset = null)

        assertThat(decoded).contains("café")
    }

    @Test
    fun `only decodes the first length bytes`() {
        val bytes = "<html>keep</html>DISCARD".toByteArray(Charsets.UTF_8)

        val decoded = decodeHtml(bytes, "<html>keep</html>".length, headerCharset = null)

        assertThat(decoded).isEqualTo("<html>keep</html>")
    }
}
