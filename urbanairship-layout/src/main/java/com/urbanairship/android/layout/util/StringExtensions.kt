package com.urbanairship.android.layout.util

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
 * - Strikethrough: `~~strikethrough~~`
 *
 * All other markdown syntax will be ignored.
 */
internal fun String.markdownToHtml(): String = basicMarkdownToHtml(this)

/** Matches `[link](url)` */
private val linkRegex = "\\[(.*?)\\]\\((.*?)\\)".toRegex()
private const val linkTag = """<a href="$2">$1</a>"""

/** Matches `***bold + italic***` or `___bold + italic___` */
private val boldAndItalicRegex = "[*_][*_][*_](.*?)[*_][*_][*_]".toRegex()
private const val boldAndItalicTag = """<b><i>$1</i></b>"""

/** Matches `**bold**` or `__bold__` */
private val boldRegex = "[*_][*_](.*?)[*_][*_]".toRegex()
private const val boldTag = """<b>$1</b>"""

/** Matches `*italic*` or `_italic_` */
private val italicRegex = "[*_](.*?)[*_]".toRegex()
private const val italicTag = """<i>$1</i>"""

/** Matches `~~strikethrough~~` */
private val strikethroughRegex = "~~(.*?)~~".toRegex()
private const val strikethroughTag = """<s>$1</s>"""

private fun basicMarkdownToHtml(markdown: String): String =
    markdown
        // Replace [link](url) with <a href="url">link</a>
        .replace(linkRegex, linkTag)
        // Replace ***bold + italic*** and ___bold + italic___ with <b><i>bold + italic</i></b>
        .replace(boldAndItalicRegex, boldAndItalicTag)
        // Replace **bold** and __bold__ with <b>bold</b>
        .replace(boldRegex, boldTag)
        // Replace *italic* and _italic_ with <i>italic</i>
        .replace(italicRegex, italicTag)
        // Replace ~~strikethrough~~ with <s>strikethrough</s>
        .replace(strikethroughRegex, strikethroughTag)
