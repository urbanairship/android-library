/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import org.junit.Assert.assertEquals
import org.junit.Test

public class HtmlSpacesTest {
    private fun String.visible() = replace(KEPT_SPACE, '~')

    @Test
    public fun singleSpacesBetweenTextStayPlain() {
        assertEquals("a b c", "a b c".preserveSpaces())
        assertEquals("<b>a</b> <i>b</i>", "<b>a</b> <i>b</i>".preserveSpaces())
    }

    @Test
    public fun aRunKeepsOnePlainSpaceForWrapping() {
        assertEquals("a~~ b", "a   b".preserveSpaces().visible())
    }

    @Test
    public fun leadingAndTrailingSpacesAreKept() {
        assertEquals("~a", " a".preserveSpaces().visible())
        assertEquals("~ a", "  a".preserveSpaces().visible())
        assertEquals("a~", "a ".preserveSpaces().visible())
    }

    @Test
    public fun spacesBesideALineBreakAreKept() {
        assertEquals("a~<br>~b", "a <br> b".preserveSpaces().visible())
    }

    @Test
    public fun runsAcrossTagsAreKept() {
        assertEquals("a~ <b>b</b>", "a  <b>b</b>".preserveSpaces().visible())
        assertEquals("<b>a</b>~ b", "<b>a</b>  b".preserveSpaces().visible())
        assertEquals("a~<b> b</b>", "a <b> b</b>".preserveSpaces().visible())
    }

    @Test
    public fun spacesInsideATagAreNotTouched() {
        assertEquals("""<a href="x y">a</a>""", """<a href="x y">a</a>""".preserveSpaces())
        assertEquals("""<font color="#000001">~a</font>""", """<font color="#000001"> a</font>""".preserveSpaces().visible())
    }
}
