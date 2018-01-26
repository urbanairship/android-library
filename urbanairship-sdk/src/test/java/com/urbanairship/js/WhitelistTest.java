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
        assertFalse(whitelist.isWhitelisted(null, Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("", Whitelist.SCOPE_OPEN_URL));
        assertFalse(whitelist.isWhitelisted("urbanairship.com", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("www.urbanairship.com", Whitelist.SCOPE_OPEN_URL));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("https://www.urbanairship.com", Whitelist.SCOPE_OPEN_URL));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com/what", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("file:///*", Whitelist.SCOPE_OPEN_URL));
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
        assertTrue(whitelist.isWhitelisted("https://device-api.urbanairship.com/api/user/", Whitelist.SCOPE_OPEN_URL));
        assertTrue(whitelist.isWhitelisted("https://device-api.urbanairship.com/api/user/", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(whitelist.isWhitelisted("https://device-api.urbanairship.com/api/user/", Whitelist.SCOPE_ALL));

        // Starbucks
        assertTrue(whitelist.isWhitelisted("https://sbux-dl.urbanairship.com/binary/token/", Whitelist.SCOPE_OPEN_URL));
        assertTrue(whitelist.isWhitelisted("https://sbux-dl.urbanairship.com/binary/token/", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(whitelist.isWhitelisted("https://sbux-dl.urbanairship.com/binary/token/", Whitelist.SCOPE_ALL));

        // Landing Page
        assertTrue(whitelist.isWhitelisted("https://dl.urbanairship.com/aaa/message_id", Whitelist.SCOPE_OPEN_URL));
        assertTrue(whitelist.isWhitelisted("https://dl.urbanairship.com/aaa/message_id", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(whitelist.isWhitelisted("https://dl.urbanairship.com/aaa/message_id", Whitelist.SCOPE_ALL));
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

        assertTrue(whitelist.isWhitelisted("https://ουτοπία.δπθ.gr", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://müller.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test wild card scheme accepts all http and https schemes.
     */
    @Test
    public void testSchemeWildCard() {
        whitelist.addEntry("*://www.urbanairship.com");

        // Reject
        assertFalse(whitelist.isWhitelisted(null, Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("urbanairship.com", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("notvalid://www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("file://www.urbanairship.com", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://www.urbanairship.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test scheme matching works.
     */
    @Test
    public void testScheme() {
        whitelist.addEntry("https://www.urbanairship.com");
        whitelist.addEntry("file:///asset.html");

        // Reject
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("file:///asset.html", Whitelist.SCOPE_ALL));
    }

    /**
     * Test regular expression on the host are treated as literals.
     */
    @Test
    public void testRegExInHost() {
        assertTrue(whitelist.addEntry("*://[a-z,A-Z]+"));

        assertFalse(whitelist.isWhitelisted("https://urbanairship", Whitelist.SCOPE_ALL));

        // It should match on a host that is equal to [a-z,A-Z]+
        assertTrue(whitelist.isWhitelisted("https://[a-z,A-Z]%2B", Whitelist.SCOPE_ALL));
    }

    /**
     * Test host matching actually works.
     */
    @Test
    public void testHost() {
        assertTrue(whitelist.addEntry("http://www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.addEntry("http://oh.hi.marc", Whitelist.SCOPE_ALL));

        // Reject
        assertFalse(whitelist.isWhitelisted("http://oh.bye.marc", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("http://www.urbanairship.com.hackers.io", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("http://omg.www.urbanairship.com.hackers.io", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://www.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://oh.hi.marc", Whitelist.SCOPE_ALL));
    }

    /**
     * Test wild card the host accepts any host.
     */
    @Test
    public void testHostWildCard() {
        assertTrue(whitelist.addEntry("http://*", Whitelist.SCOPE_ALL));

        // Reject
        assertFalse(whitelist.isWhitelisted(null, Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://what.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http:///android-asset/test.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://www.anything.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test wild card for subdomain accepts any subdomain, including no subdomain.
     */
    @Test
    public void testHostWildCardSubDomain() {
        assertTrue(whitelist.addEntry("http://*.urbanairship.com"));

        // Accept
        assertTrue(whitelist.isWhitelisted("http://what.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://hi.urbanairship.com", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://urbanairship.com", Whitelist.SCOPE_ALL));

        // Reject
        assertFalse(whitelist.isWhitelisted("http://lololurbanairship.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test wild card matcher matches any url that has a valid file path or http/https url.
     */
    @Test
    public void testWildCardMatcher() {
        assertTrue(whitelist.addEntry("*"));

        assertTrue(whitelist.isWhitelisted("file:///what/oh/hi", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://hi.urbanairship.com/path", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("http://urbanairship.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test file paths.
     */
    @Test
    public void testFilePaths() {
        assertTrue(whitelist.addEntry("file:///foo/index.html", Whitelist.SCOPE_ALL));

        // Reject
        assertFalse(whitelist.isWhitelisted("file:///foo/test.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("file:///foo/bar/index.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("file:///foooooooo/index.html", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("file:///foo/index.html", Whitelist.SCOPE_ALL));
    }

    /**
     * Test file paths with wild cards.
     */
    @Test
    public void testFilePathsWildCard() {
        assertTrue(whitelist.addEntry("file:///foo/*"));

        // Reject
        assertFalse(whitelist.isWhitelisted("file:///bar/index.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("file:///foo/test.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("file:///foo/html/index.html", Whitelist.SCOPE_ALL));
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
        assertFalse(whitelist.isWhitelisted("https://what.urbanairship.com/reject.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/image.png", Whitelist.SCOPE_ALL));

        // Accept
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/index.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/test.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://what.urbanairship.com/anythingHTML/foo/bar/index.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/what/index.html", Whitelist.SCOPE_ALL));
    }


    /**
     * Test scope.
     */
    @Test
    public void testScope() {
        whitelist.addEntry("*://*.urbanairship.com/accept-js.html", Whitelist.SCOPE_JAVASCRIPT_INTERFACE);
        whitelist.addEntry("*://*.urbanairship.com/accept-url.html", Whitelist.SCOPE_OPEN_URL);
        whitelist.addEntry("*://*.urbanairship.com/accept-all.html", Whitelist.SCOPE_ALL);

        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/accept-js.html", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("https://urbanairship.com/accept-js.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("https://urbanairship.com/accept-js.html", Whitelist.SCOPE_OPEN_URL));

        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/accept-url.html", Whitelist.SCOPE_OPEN_URL));
        assertFalse(whitelist.isWhitelisted("https://urbanairship.com/accept-url.html", Whitelist.SCOPE_ALL));
        assertFalse(whitelist.isWhitelisted("https://urbanairship.com/accept-url.html", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));

        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/accept-all.html", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/accept-all.html", Whitelist.SCOPE_ALL));
        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/accept-all.html", Whitelist.SCOPE_OPEN_URL));
    }

    /**
     * Test disabling url scope whitelisting.
     */
    @Test
    public void testDisableUrlScopeWhitelisting() {
        assertFalse(whitelist.isWhitelisted("https://someurl.com", Whitelist.SCOPE_OPEN_URL));

        whitelist.setOpenUrlWhitelistingEnabled(false);
        assertTrue(whitelist.isWhitelisted("https://someurl.com", Whitelist.SCOPE_OPEN_URL));
        assertFalse(whitelist.isWhitelisted("https://someurl.com", Whitelist.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(whitelist.isWhitelisted("https://someurl.com", Whitelist.SCOPE_ALL));
    }

    /**
     * Test SCOPE_ALL whitelist check if two separate entries match for both types of scope.
     */
    @Test
    public void testScopeAll() {
        whitelist.addEntry("*", Whitelist.SCOPE_JAVASCRIPT_INTERFACE);
        whitelist.addEntry("*://*.urbanairship.com/all.html", Whitelist.SCOPE_OPEN_URL);

        assertTrue(whitelist.isWhitelisted("https://urbanairship.com/all.html", Whitelist.SCOPE_ALL));
    }
}


