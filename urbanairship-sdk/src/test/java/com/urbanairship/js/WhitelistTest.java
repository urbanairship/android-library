/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.js;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class WhitelistTest extends BaseTestCase {

    Whitelist whitelist;

    @Before
    public void setup() {
        whitelist = new Whitelist();
    }

    /**
     * Test an empty white list rejects all URLs.
     */
    @Test
    public void testEmptyWhiteList() {
        assertFalse(whitelist.isWhitelisted(null));
        assertFalse(whitelist.isWhitelisted(""));
        assertFalse(whitelist.isWhitelisted("urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("www.urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("https://www.urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com/what"));
        assertFalse(whitelist.isWhitelisted("file:///*"));
    }

    /**
     * Test the default white list accepts Urban Airship URLs.
     */
    @Test
    public void testDefaultWhitelist() {
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        Whitelist whitelist = Whitelist.createDefaultWhitelist(airshipConfigOptions);

        // Messages
        assertTrue(whitelist.isWhitelisted("https://device-api.urbanairship.com/api/user/"));

        // Starbucks
        assertTrue(whitelist.isWhitelisted("https://sbux-dl.urbanairship.com/binary/token/"));

        // Landing Page
        assertTrue(whitelist.isWhitelisted("https://dl.urbanairship.com/aaa/message_id"));
    }

    /**
     * Test setting invalid patterns returns false.
     */
    @Test
    public void testInvalidPatterns() {
        // Not a URL
        assertFalse(whitelist.addEntry("not a url"));
        assertFalse(whitelist.addEntry(null));

        // Missing schemes
        assertFalse(whitelist.addEntry("www.urbanairship.com"));
        assertFalse(whitelist.addEntry("://www.urbanairship.com"));

        // Invalid schemes
        assertFalse(whitelist.addEntry("what://*"));
        assertFalse(whitelist.addEntry("ftp://*"));
        assertFalse(whitelist.addEntry("sftp://*"));

        // White space in scheme
        assertFalse(whitelist.addEntry(" file://*"));

        // Invalid hosts
        assertFalse(whitelist.addEntry("*://what*"));
        assertFalse(whitelist.addEntry("*://*what"));

        // Missing host
        assertFalse(whitelist.addEntry("*://"));

        // Missing file path
        assertFalse(whitelist.addEntry("file://"));

        // Invalid file path
        assertFalse(whitelist.addEntry("file://*"));
    }

    /**
     * Test international URLs.
     */
    @Test
    public void testInternationalURLs() {
        assertTrue(whitelist.addEntry("*://ουτοπία.δπθ.gr"));
        assertTrue(whitelist.addEntry("*://müller.com"));

        assertTrue(whitelist.isWhitelisted("https://ουτοπία.δπθ.gr"));
        assertTrue(whitelist.isWhitelisted("https://müller.com"));
    }

    /**
     * Test wild card scheme accepts all http and https schemes.
     */
    @Test
    public void testSchemeWildCard() {
        whitelist.addEntry("*://www.urbanairship.com");

        // Reject
        assertFalse(whitelist.isWhitelisted(null));
        assertFalse(whitelist.isWhitelisted(""));
        assertFalse(whitelist.isWhitelisted("urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("www.urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("notvalid://www.urbanairship.com"));
        assertFalse(whitelist.isWhitelisted("file://www.urbanairship.com"));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://www.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("http://www.urbanairship.com"));
    }

    /**
     * Test scheme matching works.
     */
    @Test
    public void testScheme() {
        whitelist.addEntry("https://www.urbanairship.com");
        whitelist.addEntry("file:///asset.html");

        // Reject
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com"));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://www.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("file:///asset.html"));
    }

    /**
     * Test regular expression on the host are treated as literals.
     */
    @Test
    public void testRegExInHost() {
        assertTrue(whitelist.addEntry("*://[a-z,A-Z]+"));

        assertFalse(whitelist.isWhitelisted("https://urbanairship"));

        // It should match on a host that is equal to [a-z,A-Z]+
        assertTrue(whitelist.isWhitelisted("https://[a-z,A-Z]%2B"));
    }

    /**
     * Test host matching actually works.
     */
    @Test
    public void testHost() {
        assertTrue(whitelist.addEntry("http://www.urbanairship.com"));
        assertTrue(whitelist.addEntry("http://oh.hi.marc"));

        // Reject
        assertFalse(whitelist.isWhitelisted("http://oh.bye.marc"));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com.hackers.io"));
        assertFalse(whitelist.isWhitelisted("http://omg.www.urbanairship.com.hackers.io"));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://www.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("http://oh.hi.marc"));
    }

    /**
     * Test wild card the host accepts any host.
     */
    @Test
    public void testHostWildCard() {
        assertTrue(whitelist.addEntry("http://*"));

        // Reject
        assertFalse(whitelist.isWhitelisted(null));
        assertFalse(whitelist.isWhitelisted(""));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://what.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("http:///android-asset/test.html"));
        assertTrue(whitelist.isWhitelisted("http://www.anything.com"));
    }

    /**
     * Test wild card for subdomain accepts any subdomain, including no subdomain.
     */
    @Test
    public void testHostWildCardSubDomain() {
        assertTrue(whitelist.addEntry("http://*.urbanairship.com"));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://what.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("http://hi.urbanairship.com"));
        assertTrue(whitelist.isWhitelisted("http://urbanairship.com"));

        // Reject
        assertFalse(whitelist.isWhitelisted("http://lololurbanairship.com"));
    }

    /**
     * Test wild card matcher matches any url that has a valid file path or http/https url.
     */
    @Test
    public void testWildCardMatcher() {
        assertTrue(whitelist.addEntry("*"));

        assertTrue(whitelist.isWhitelisted("file:///what/oh/hi"));
        assertTrue(whitelist.isWhitelisted("https://hi.urbanairship.com/path"));
        assertTrue(whitelist.isWhitelisted("http://urbanairship.com"));
    }

    /**
     * Test file paths.
     */
    @Test
    public void testFilePaths() {
        assertTrue(whitelist.addEntry("file:///foo/index.html"));

        // Reject
        assertFalse(whitelist.isWhitelisted("file:///foo/test.html"));
        assertFalse(whitelist.isWhitelisted("file:///foo/bar/index.html"));
        assertFalse(whitelist.isWhitelisted("file:///foooooooo/index.html"));

        // Accept
        assertTrue(whitelist.isWhitelisted("file:///foo/index.html"));
    }

    /**
     * Test file paths with wild cards.
     */
    @Test
    public void testFilePathsWildCard() {
        assertTrue(whitelist.addEntry("file:///foo/*"));

        // Reject
        assertFalse(whitelist.isWhitelisted("file:///bar/index.html"));
        assertFalse(whitelist.isWhitelisted(""));

        // Accept
        assertTrue(whitelist.isWhitelisted("file:///foo/test.html"));
        assertTrue(whitelist.isWhitelisted("file:///foo/html/index.html"));
    }

    /**
     * Test paths in http/https URLs.
     */
    @Test
    public void testURLPaths() {
        whitelist.addEntry("*://*.urbanairship.com/accept.html");
        whitelist.addEntry("*://*.urbanairship.com/anythingHTML/*.html");
        whitelist.addEntry("https://urbanairship.com/what/index.html");


        // Reject
        assertFalse(whitelist.isWhitelisted("https://what.urbanairship.com/reject.html"));
        assertFalse(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/image.png"));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/index.html"));
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/test.html"));
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/foo/bar/index.html"));
        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/what/index.html"));
    }
}


