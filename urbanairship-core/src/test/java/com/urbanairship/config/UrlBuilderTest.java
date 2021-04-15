package com.urbanairship.config;

import android.net.Uri;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UrlBuilderTest extends BaseTestCase {

    @Test
    public void testNullUrl() {
        Uri url = new UrlBuilder(null).build();
        assertNull(url);
    }

    @Test
    public void testNullUrlAppended() {
        Uri url = new UrlBuilder(null)
                .appendPath("neat")
                .appendEncodedPath("rad/story")
                .appendQueryParameter("cool", "story")
                .build();
        assertNull(url);
    }

    @Test
    public void testMalformedUrl() {
        Uri url = new UrlBuilder("notaurl")
                .build();
        assertEquals("notaurl", url.toString());
    }

    @Test
    public void testUrl() {
        Uri url = new UrlBuilder("https://neat.com")
                .appendPath("neat")
                .appendEncodedPath("rad/story")
                .appendQueryParameter("cool", "story")
                .build();


        assertEquals(Uri.parse("https://neat.com/neat/rad/story?cool=story"), url);
    }
}
