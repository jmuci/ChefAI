package com.tenmilelabs.chefai.core.ui.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The guardrail for the Modernist redesign: **no screen writes a color**.
 *
 * The redesign lands over many PRs, and dark mode is designed after all of them. That only works
 * if every color on every screen resolves through a theme role, so that one edit to
 * [lightScheme]/[darkScheme]/[LightChefColors] retheme the whole app. A single
 * `Color(0xFF0A8080)` in a screen is a value that the dark theme cannot reach, and it will not be
 * noticed until someone opens that screen in dark mode months later.
 *
 * So this walks the UI source and fails on the two ways that happens:
 *
 *  1. a hard-coded `Color(0x…)` literal, and
 *  2. alpha applied to an ink role (`onBackground.copy(alpha = …)`) to fake muted text.
 *
 * The second is the subtle one, and the design handoff calls it out by name: alpha mixes ink
 * *toward the ground*, so a value tuned to read as "65% ink" on the light ground collapses to
 * near-invisible on a dark one. Muted ink is a resolved color per theme
 * ([ModernistPalette.InkMuted]), exposed as `colorScheme.onSurfaceVariant`.
 *
 * `core/ui/theme/` itself is allowlisted — it is where the palette is *defined*.
 */
class NoLiteralColorsTest {

    @Test
    fun `no color literals in ui code`() {
        val offenders = scanUiSources { line -> COLOR_LITERAL.containsMatchIn(line) }

        assertWithMessage(
            """
            |Color literals found in UI code. The Modernist redesign is themed end to end, and a
            |literal is a color the dark theme cannot reach.
            |
            |${offenders.joinToString("\n") { "  $it" }}
            |
            |Fix: replace the literal with the role it stands for.
            |
            |  #f3f2f2 background  -> MaterialTheme.colorScheme.background
            |  #201e1d ink         -> MaterialTheme.colorScheme.onBackground
            |  muted ink           -> MaterialTheme.colorScheme.onSurfaceVariant
            |  #0A8080 accent      -> MaterialTheme.colorScheme.primary
            |  divider / rule      -> MaterialTheme.colorScheme.outline
            |  neutral-200 fill    -> MaterialTheme.colorScheme.surfaceVariant
            |  accent ramp step    -> MaterialTheme.chefColors.accent.s100 … .s900
            |  neutral ramp step   -> MaterialTheme.chefColors.neutral.s100 … .s900
            |  nav sage #e7f0e0    -> MaterialTheme.chefColors.navSurface
            |
            |If the color you need genuinely has no role yet, add one to ChefColors (and its dark
            |counterpart) in core/ui/theme/ rather than allowlisting the call site. The full table
            |is in docs/design/modernist.md.
            """.trimMargin(),
        ).that(offenders).isEmpty()
    }

    @Test
    fun `no alpha-muted ink in ui code`() {
        val offenders = scanUiSources { line -> ALPHA_MUTED_INK.containsMatchIn(line) }

        assertWithMessage(
            """
            |Ink muted with alpha in UI code:
            |
            |${offenders.joinToString("\n") { "  $it" }}
            |
            |Alpha on an ink role mixes it toward whatever is behind it, so a value tuned to read
            |correctly on the light ground loses its contrast on a dark one — the muted 12px
            |captions this is usually used for are exactly the text that cannot afford it.
            |
            |Fix: use the resolved role instead of computing one.
            |
            |  onBackground.copy(alpha = 0.6f)  -> MaterialTheme.colorScheme.onSurfaceVariant
            |  onSurface.copy(alpha = 0.4f)     -> MaterialTheme.colorScheme.outline  (rules, not text)
            |
            |For a genuinely disabled control, alpha on the *whole composable* is fine — it is
            |alpha standing in for a color that is the problem here. If you need a third ink level,
            |add it to ChefColors with a resolved value for each theme.
            |
            |See docs/design/modernist.md § Dark-mode discipline.
            """.trimMargin(),
        ).that(offenders).isEmpty()
    }

    @Test
    fun `the scan covers the ui source tree and the allowlisted theme package still exists`() {
        // Both halves of this test are load-bearing. If the source layout moves and the walk finds
        // nothing, the two tests above pass vacuously and the guardrail is silently off. If
        // core/ui/theme/ is renamed without updating ALLOWLIST, the palette itself starts failing.
        assertThat(uiSourceFiles()).isNotEmpty()
        assertThat(File(mainSourceRoot(), THEME_PACKAGE).isDirectory).isTrue()
    }

    // ── plumbing ──────────────────────────────────────────────────────────────────────────────

    /** `file:line: text` for every non-comment line in the UI tree that [isOffending] flags. */
    private fun scanUiSources(isOffending: (String) -> Boolean): List<String> =
        uiSourceFiles().flatMap { file ->
            file.readText()
                .stripBlockComments()
                .lineSequence()
                .map { it.stripLineComment() }
                .withIndex()
                .filter { (_, line) -> isOffending(line) }
                .map { (index, line) ->
                    "${file.relativeTo(mainSourceRoot()).invariantPath()}:${index + 1}: ${line.trim()}"
                }
                .toList()
        }

    private fun uiSourceFiles(): List<File> {
        val root = mainSourceRoot()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.relativeTo(root).invariantPath() }
            .filter { (_, path) -> path.contains("/ui/") }
            .filterNot { (_, path) -> path.startsWith(THEME_PACKAGE) }
            .map { (file, _) -> file }
            .toList()
    }

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private companion object {
        /** Package path, relative to the source root, that is allowed to name colors. */
        const val THEME_PACKAGE = "com/tenmilelabs/chefai/core/ui/theme"

        val COLOR_LITERAL = Regex("""Color\(\s*0x[0-9a-fA-F]+""")

        /**
         * `onBackground`/`onSurface`/`onSurfaceVariant` — or the raw ink token — with `.copy(alpha`
         * hung off it. Allows an intervening `MaterialTheme.colorScheme.` prefix and whitespace.
         */
        val ALPHA_MUTED_INK = Regex(
            """\b(onBackground|onSurface|onSurfaceVariant|Ink)\s*\.\s*copy\s*\(\s*alpha""",
        )

        private var cachedRoot: File? = null

        /**
         * `app/src/main/java`, found by walking up from the test's working directory. Gradle runs
         * unit tests from the module directory, but IDE run configurations vary, so this searches
         * rather than assuming.
         */
        fun mainSourceRoot(): File = cachedRoot ?: run {
            val candidates = listOf("src/main/java", "app/src/main/java")
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                for (candidate in candidates) {
                    val root = File(dir, candidate)
                    if (root.isDirectory) {
                        cachedRoot = root
                        return root
                    }
                }
                dir = dir.parentFile
            }
            error(
                "Could not locate app/src/main/java from ${File("").absolutePath}. " +
                    "NoLiteralColorsTest scans source files on disk; if the module layout moved, " +
                    "update the candidate paths here.",
            )
        }

        fun String.stripLineComment(): String = substringBefore("//")

        fun String.stripBlockComments(): String =
            replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { match ->
                // Keep the line count stable so reported line numbers stay accurate.
                "\n".repeat(match.value.count { it == '\n' })
            }
    }
}
