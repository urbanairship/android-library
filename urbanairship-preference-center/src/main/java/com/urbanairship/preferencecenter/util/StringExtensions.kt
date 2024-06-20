package com.urbanairship.preferencecenter.util

import org.intellij.lang.annotations.Language

/**
 * Returns the emoji flag for a given ISO 3166-1 alpha-2 country code.
 *
 * If the country code is not an alpha-2 country code, `null` will be returned.
 * If the country code is not a valid alpha-2 country code, the result will be a question-mark
 * flag, though this behavior may differ depending on Android version and OEM.
 */
internal val String.emojiFlag: String?
    get() = countryFlag(this)


private fun countryFlag(code: String): String? {
    val sanitizedCode = code.uppercase().replace(Regex("[^A-Z]"), "")
    if (sanitizedCode.length != 2) {
        return null
    }

    return sanitizedCode.map { it.code + 0x1F1A5 }.joinToString("") {
        Character.toChars(it).concatToString()
    }
}

/**
 * Converts basic markdown to HTML.
 *
 * Currently supported markdown syntax includes:
 * - Links: `[link](url)`
 * - Bold: `**bold**`
 * - Italic: `*italic*`
 * - Underline: `__underline__`
 *
 * All other markdown syntax will be ignored.
 */
internal fun String.markdownToHtml(): String = basicMarkdownToHtml(this)

private val linkRegex = "\\[(.*?)\\]\\((.*?)\\)".toRegex()
private val boldRegex = "\\*\\*(.*?)\\*\\*".toRegex()
private val italicRegex = "\\*(.*?)\\*".toRegex()
private val underlineRegex = "__(.*?)__".toRegex()

private const val linkTag = """<a href="$2">$1</a>"""
private const val boldTag = """<b>$1</b>"""
private const val italicTag = """<i>$1</i>"""
private const val underlineTag = """<u>$1</u>"""

private fun basicMarkdownToHtml(markdown: String): String =
    markdown
        // Replace [link](url) with <a href="url">link</a>
        .replace(linkRegex, linkTag)
        // Replace **bold** with <b>bold</b>
        .replace(boldRegex, boldTag)
        // Replace *italic* with <i>italic</i>
        .replace(italicRegex, italicTag)
        // Replace __underline__ with <u>underline</u>
        .replace(underlineRegex, underlineTag)
