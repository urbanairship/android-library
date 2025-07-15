package com.urbanairship.config

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UrlBuilderTest {

    @Test
    public fun testNullUrl() {
        val url = UrlBuilder(null).build()
        Assert.assertNull(url)
    }

    @Test
    public fun testNullUrlAppended() {
        val url = UrlBuilder(null)
            .appendPath("neat")
            .appendEncodedPath("rad/story")
            .appendQueryParameter("cool", "story")
            .build()
        Assert.assertNull(url)
    }

    @Test
    public fun testMalformedUrl() {
        val url = UrlBuilder("notaurl").build()
        Assert.assertEquals("notaurl", url.toString())
    }

    @Test
    public fun testUrl() {
        val url = UrlBuilder("https://neat.com")
            .appendPath("neat")
            .appendEncodedPath("rad/story")
            .appendQueryParameter("cool", "story")
            .build()

        Assert.assertEquals(Uri.parse("https://neat.com/neat/rad/story?cool=story"), url)
    }
}
