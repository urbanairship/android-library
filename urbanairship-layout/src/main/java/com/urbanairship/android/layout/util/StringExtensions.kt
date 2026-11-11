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
 * `\*not italic\*` draws `*not italic*`, and `\[x]{color:name}` isn't a color span.
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

/** The code points escaped characters stand in as, which flanking reads as punctuation. */
internal val ESCAPED_CHAR_RANGE: IntRange = escaped('!').code..escaped('~').code

/** Matches an escaped character's stand-in. */
private val escapedCharRegex = "[${escaped('!')}-${escaped('~')}]".toRegex()

/**
 * Matches `[text]{color:name}` whose text holds no brackets other than complete `[link](url)`s,
 * so nested spans match innermost first. An escaped bracket is a stand-in by now, so it's text.
 */
private val colorSpanRegex =
    """\[((?:[^\[\]]|\[[^\[\]]*]\([^)]*\))+?)]\{color:([\p{L}\p{N}_-]+)\}""".toRegex()

/**
 * Matches `[link](url)` or `[link](url "title")` whose text holds no brackets, so a bracket before
 * the link stays literal. Runs on HTML-encoded text, so a title's quotes are `&quot;` or `&#39;`.
 * The URL may hold one level of balanced parentheses, as in CommonMark.
 */
private val linkRegex =
    """\[([^\[\]]*)]\(\s*((?:[^\s()]|\([^\s()]*\))+)(?:\s+(?:&quot;(?:(?!&quot;).)*&quot;|&#39;(?:(?!&#39;).)*&#39;))?\s*\)"""
        .toRegex()

/** Characters the superscript, subscript and highlight passes read, which a URL holds as stand-ins. */
private val markupInUrlRegex = """[=^,{}]""".toRegex()

/** Returns the `<a>` tag for a [linkRegex] match, with [markupInUrlRegex] characters in its URL as stand-ins. */
private fun linkTag(match: MatchResult): String {
    val (text, url) = match.destructured
    val href = url.replace(markupInUrlRegex) { escaped(it.value[0]).toString() }
    return """<a href="$href">$text</a>"""
}

/** Matches `^^superscript^^` */
private val superscriptRegex = """\^\^(.+?)\^\^""".toRegex()
private const val superscriptTag = """<sup>$1</sup>"""

/** Matches `,{subscript},` */
private val subscriptRegex = """,\{(.+?)\},""".toRegex()
private const val subscriptTag = """<sub>$1</sub>"""

/** Matches `==highlight==` */
private val highlightRegex = """==([\s\S]+?)==""".toRegex()
/** Color is replaced later **/
private const val highlightTag = """<$HIGHLIGHT_TAG>$1</$HIGHLIGHT_TAG>"""

/** Newline character `\n` */
private const val newline = "\n"
private const val newlineEscaped = "\\n"
internal const val newlineTag = "<br>"


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
                """<font color="$placeholder">$text</font>"""
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
        // Replace [text]{color:name} with a placeholder-colored tag, before links so a
        // link regex can't swallow the brackets
        .replaceColorSpans(colorNames)
        // Replace newlines with <br>
        .replace(newline, newlineTag)
        .replace(newlineEscaped, newlineTag)
        // Replace [link](url) with <a href="url">link</a>
        .replace(linkRegex, ::linkTag)
        // Replace ^^superscript^^ with <sup>superscript</sup>
        .replace(superscriptRegex, superscriptTag)
        // Replace ,{subscript}, with <sub>subscript</sub>
        .replace(subscriptRegex, subscriptTag)
        // Replace ==highlight== with <uahighlight>highlight</uahighlight>
        .replace(highlightRegex, highlightTag)
        // Replace **bold**, *italic*, ***both*** and ~strikethrough~ with tags, last so a marker
        // only pairs with one inside the same link, color, superscript, subscript or highlight
        .applyEmphasis()
        // Draw escaped characters as themselves
        .replace(escapedCharRegex) { (it.value[0].code - ESCAPE_OFFSET).toChar().toString().htmlEncode() }
