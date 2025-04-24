package com.urbanairship.preferencecenter.util

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
