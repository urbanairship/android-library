package com.urbanairship.config;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UrlBuilderTest extends BaseTestCase {

    @Test
    public void testNullUrl() {
        URL url = new UrlBuilder(null).build();
        assertNull(url);
    }

    @Test
    public void testNullUrlAppended() {
        URL url = new UrlBuilder(null)
                .appendPath("neat")
                .appendEncodedPath("rad/story")
                .appendQueryParameter("cool", "story")
                .build();
        assertNull(url);
    }

    @Test
    public void testMalformedUrl() {
        URL url = new UrlBuilder("notaurl")
                .build();
        assertNull(url);
    }

    @Test
    public void testUrl() throws MalformedURLException {
        URL url = new UrlBuilder("https://neat.com")
                .appendPath("neat")
                .appendEncodedPath("rad/story")
                .appendQueryParameter("cool", "story")
                .build();


        assertEquals(new URL("https://neat.com/neat/rad/story?cool=story"), url);
    }
}
