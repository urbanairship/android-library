package com.urbanairship.android.layout.util

import androidx.core.text.htmlEncode
import java.util.Locale

internal fun String?.ifNotEmpty(block: (String) -> Unit) {
    if (!this.isNullOrEmpty()) {
        block(this)
    }
}

/**
 * Converts basic markdown to HTML.
 *
 * Currently supported markdown syntax includes:
 * - Links: `[link](url)`
 * - Bold: `**bold**`
 * - Italic: `*italic*`
 * - Bold and italic: `***bold + italic***`
 * - Strikethrough: `~~strikethrough~~` or `~strikethrough~`
 * - Highlight: `==highlight==`
 * - Superscript: `^^superscript^^`
 * - Subscript: `,{subscript},`
 * - Color: `[text]{color:name}`, where `name` is one of [colorNames]
 *
 * A backslash before ASCII punctuation draws that character literally, as in CommonMark:
 * `\*not italic\*` draws `*not italic*`. An escaped bracket still opens or closes a color span,
 * which is what iOS does.
 *
 * All other markdown syntax will be ignored.
 *
 * @param colorNames the scene's named colors' names; a color span's placeholder color encodes its
 *   name's index here (see [colorSpanIndex]), and a span naming anything else keeps only its text.
 */
internal fun String.markdownToHtml(colorNames: List<String> = emptyList()): String =
    basicMarkdownToHtml(this, colorNames)

/**
 * Returns the [markdownToHtml] name index a color span's placeholder [color] encodes, or `null` if it isn't one.
 *
 * Every foreground color in converted markdown comes from a color span, since the input is
 * HTML-encoded before any tags are added.
 */
internal fun colorSpanIndex(color: Int): Int? = ((color and 0xFFFFFF) - 1).takeIf { it >= 0 }

/** Matches a backslash before ASCII punctuation, which draws that character literally. */
private val backslashEscapeRegex = """\\([!-/:-@\[-`{-~])""".toRegex()

/** Escaped characters stand in as these private-use characters until the markdown is converted. */
private const val ESCAPE_OFFSET = 0xE000

private fun escaped(char: Char): Char = (ESCAPE_OFFSET + char.code).toChar()

private val escapedOpenBracket = escaped('[')
private val escapedCloseBracket = escaped(']')

/** Matches an escaped character's stand-in. */
private val escapedCharRegex = "[${escaped('!')}-${escaped('~')}]".toRegex()

/**
 * Matches `[text]{color:name}` whose text holds no brackets other than complete `[link](url)`s,
 * so nested spans match innermost first.
 */
private val colorSpanRegex = run {
    val open = """[\[$escapedOpenBracket]"""
    val close = """[\]$escapedCloseBracket]"""
    val notBracket = """[^\[\]$escapedOpenBracket$escapedCloseBracket]"""
    """$open((?:$notBracket|$open$notBracket*$close\([^)]*\))+?)$close\{color:([\p{L}\p{N}_-]+)\}""".toRegex()
}

/** Matches `[link](url)` */
private val linkRegex = """\[(.+?)]\((.+?)\)""".toRegex()
private const val linkTag = """<a href="$2">$1</a>"""

/** Matches `^^superscript^^` */
private val superscriptRegex = """\^\^(.+?)\^\^""".toRegex()
private const val superscriptTag = """<sup>$1</sup>"""

/** Matches `,{subscript},` */
private val subscriptRegex = """,\{(.+?)\},""".toRegex()
private const val subscriptTag = """<sub>$1</sub>"""

/** Matches `==highlight==` */
private val highlightRegex = """==([\s\S]+?)==""".toRegex()
/** Color is replaced later **/
private const val highlightTag = """<span style="background-color: #4DFFFF00;">$1</span>"""

/** Newline character `\n` */
private const val newline = "\n"
private const val newlineEscaped = "\\n"
private const val newlineTag = "<br>"


/** Replaces color spans until none are left, so each pass unwraps one level of nesting. */
private fun String.replaceColorSpans(colorNames: List<String>): String {
    var result = this
    while (true) {
        val next = colorSpanRegex.replace(result) { match ->
            val (text, name) = match.destructured
            val index = colorNames.indexOf(name)
            if (index < 0) {
                text
            } else {
                val placeholder = "#%06X".format(index + 1)
                """<span style="color: $placeholder;">$text</span>"""
            }
        }
        if (next == result) return result
        result = next
    }
}

private fun basicMarkdownToHtml(markdown: String, colorNames: List<String>): String =
    markdown
        // Swap escaped characters for stand-ins no markdown syntax matches
        .replace(backslashEscapeRegex) { escaped(it.groupValues[1][0]).toString() }
        // Encode any HTML characters in the original input so that we don't lose
        // symbols like < or & after converting to HTML and rendering the text.
        // This must be done first, before any other formatting is applied.
        .htmlEncode()
        // Replace [text]{color:name} with a placeholder-colored <span>, before links so a
        // link regex can't swallow the brackets
        .replaceColorSpans(colorNames)
        // Replace newlines with <br>
        .replace(newline, newlineTag)
        .replace(newlineEscaped, newlineTag)
        // Replace [link](url) with <a href="url">link</a>
        .replace(linkRegex, linkTag)
        // Replace **bold**, *italic*, ***both*** and ~strikethrough~ with tags
        .applyEmphasis()
        // Replace ^^superscript^^ with <sup>superscript</sup>
        .replace(superscriptRegex, superscriptTag)
        // Replace ,{subscript}, with <sub>subscript</sub>
        .replace(subscriptRegex, subscriptTag)
        // Replace ==highlight== with <span style="background-color: #4DFFFF00;">highlight</span>
        .replace(highlightRegex, highlightTag)
        // Draw escaped characters as themselves
        .replace(escapedCharRegex) { (it.value[0].code - ESCAPE_OFFSET).toChar().toString().htmlEncode() }
