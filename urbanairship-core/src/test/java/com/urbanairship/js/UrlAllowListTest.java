/* Copyright Airship and Contributors */

package com.urbanairship.js;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

public class UrlAllowListTest extends BaseTestCase {

    UrlAllowList urlAllowList;

    @Before
    public void setup() {
        urlAllowList = new UrlAllowList();
    }

    /**
     * Test an empty urlAllowList rejects all URLs.
     */
    @Test
    public void testEmptyUrlAllowList() {
        assertFalse(urlAllowList.isAllowed(null, UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("urbanairship.com", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("www.urbanairship.com", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("http://www.urbanairship.com", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("https://www.urbanairship.com", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("http://www.urbanairship.com/what", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("file:///*", UrlAllowList.SCOPE_OPEN_URL));
    }

    /**
     * Test the default urlAllowList accepts Airship URLs.
     */
    @Test
    public void testDefaultUrlAllowList() {
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        UrlAllowList urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions);

        // Messages
        assertTrue(urlAllowList.isAllowed("https://device-api.urbanairship.com/api/user/", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("https://device-api.urbanairship.com/api/user/", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(urlAllowList.isAllowed("https://device-api.urbanairship.com/api/user/", UrlAllowList.SCOPE_ALL));

        // Starbucks
        assertTrue(urlAllowList.isAllowed("https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(urlAllowList.isAllowed("https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.SCOPE_ALL));

        // Youtube
        assertTrue(urlAllowList.isAllowed("https://www.youtube.com/embed/wJelEXaPhJ8", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("https://www.youtube.com/embed/wJelEXaPhJ8", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("https://www.youtube.com/embed/wJelEXaPhJ8", UrlAllowList.SCOPE_ALL));

        // EU
        assertTrue(urlAllowList.isAllowed("https://dl.asnapieu.com/binary/token/", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("https://dl.asnapieu.com/binary/token/", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(urlAllowList.isAllowed("https://dl.asnapieu.com/binary/token/", UrlAllowList.SCOPE_ALL));

        // sms
        assertTrue(urlAllowList.isAllowed("sms:+18675309?body=Hi%20you", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("sms:8675309", UrlAllowList.SCOPE_OPEN_URL));

        // tel
        assertTrue(urlAllowList.isAllowed("tel:+18675309", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("tel:867-5309", UrlAllowList.SCOPE_OPEN_URL));

        // email
        assertTrue(urlAllowList.isAllowed("mailto:name@example.com?subject=The%20subject%20of%20the%20mail", UrlAllowList.SCOPE_OPEN_URL));
        assertTrue(urlAllowList.isAllowed("mailto:name@example.com", UrlAllowList.SCOPE_OPEN_URL));
    }

    /**
     * Test setting invalid patterns returns false.
     */
    @Test
    public void testInvalidPatterns() {
        // Not a URL
        assertFalse(urlAllowList.addEntry("not a url"));

        // Missing schemes
        assertFalse(urlAllowList.addEntry("www.urbanairship.com"));
        assertFalse(urlAllowList.addEntry("://www.urbanairship.com"));

        // White space in scheme
        assertFalse(urlAllowList.addEntry(" file://*"));

        // Invalid hosts
        assertFalse(urlAllowList.addEntry("*://what*"));
        assertFalse(urlAllowList.addEntry("*://*what"));
    }

    /**
     * Test international URLs.
     */
    @Test
    public void testInternationalURLs() {
        assertTrue(urlAllowList.addEntry("*://ουτοπία.δπθ.gr"));
        assertTrue(urlAllowList.addEntry("*://müller.com"));

        assertTrue(urlAllowList.isAllowed("https://ουτοπία.δπθ.gr", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://müller.com", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test wild card scheme with wild cards.
     */
    @Test
    public void testSchemeWildCard() {
        assertTrue(urlAllowList.addEntry("*://www.urbanairship.com"));
        assertTrue(urlAllowList.addEntry("cool*story://rad"));

        // Reject
        assertFalse(urlAllowList.isAllowed(null, UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("cool://rad", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("https://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("file://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("valid://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("cool----story://rad", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("coolstory://rad", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test scheme matching works.
     */
    @Test
    public void testScheme() {
        urlAllowList.addEntry("https://www.urbanairship.com");
        urlAllowList.addEntry("file:///asset.html");

        // Reject
        assertFalse(urlAllowList.isAllowed("http://www.urbanairship.com", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("https://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("file:///asset.html", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test regular expression on the host and schema are treated as literals.
     */
    @Test
    public void testRegExEscaped() {
        assertTrue(urlAllowList.addEntry("w+.+://[a-z,A-Z]+"));

        assertFalse(urlAllowList.isAllowed("wwww://urbanairship", UrlAllowList.SCOPE_ALL));

        // It should match on a host that is equal to [a-z,A-Z]+
        assertTrue(urlAllowList.isAllowed("w+.+://[a-z,A-Z]%2B", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test host matching actually works.
     */
    @Test
    public void testHost() {
        assertTrue(urlAllowList.addEntry("http://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.addEntry("http://oh.hi.marc", UrlAllowList.SCOPE_ALL));

        // Reject
        assertFalse(urlAllowList.isAllowed("http://oh.bye.marc", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("http://www.urbanairship.com.hackers.io", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("http://omg.www.urbanairship.com.hackers.io", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("http://www.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://oh.hi.marc", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test wild card in the host.
     */
    @Test
    public void testHostWildCard() {
        assertTrue(urlAllowList.addEntry("http://*", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.addEntry("https://*.coolstory", UrlAllowList.SCOPE_ALL));

        // * is only available at the beginning
        assertFalse(urlAllowList.addEntry("https://*.coolstory.*", UrlAllowList.SCOPE_ALL));

        // Reject
        assertFalse(urlAllowList.isAllowed(null, UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("https://cool", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("https://story", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("http://what.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http:///android-asset/test.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://www.anything.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://coolstory", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://what.coolstory", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://what.what.coolstory", UrlAllowList.SCOPE_ALL));

    }

    /**
     * Test wild card for subdomain accepts any subdomain, including no subdomain.
     */
    @Test
    public void testHostWildCardSubDomain() {
        assertTrue(urlAllowList.addEntry("http://*.urbanairship.com"));

        // Accept
        assertTrue(urlAllowList.isAllowed("http://what.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://hi.urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://urbanairship.com", UrlAllowList.SCOPE_ALL));

        // Reject
        assertFalse(urlAllowList.isAllowed("http://lololurbanairship.com", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test wild card matcher matches any url.
     */
    @Test
    public void testWildCardMatcher() {
        assertTrue(urlAllowList.addEntry("*"));

        assertTrue(urlAllowList.isAllowed("file:///what/oh/hi", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://hi.urbanairship.com/path", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("http://urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("cool.story://urbanairship.com", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("sms:+18664504185?body=Hi", UrlAllowList.SCOPE_ALL));

    }

    /**
     * Test file paths.
     */
    @Test
    public void testFilePaths() {
        assertTrue(urlAllowList.addEntry("file:///foo/index.html", UrlAllowList.SCOPE_ALL));

        // Reject
        assertFalse(urlAllowList.isAllowed("file:///foo/test.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("file:///foo/bar/index.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("file:///foooooooo/index.html", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("file:///foo/index.html", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test file paths with wild cards.
     */
    @Test
    public void testFilePathsWildCard() {
        assertTrue(urlAllowList.addEntry("file:///foo/*"));

        // Reject
        assertFalse(urlAllowList.isAllowed("file:///bar/index.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("file:///foo/test.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("file:///foo/html/index.html", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test paths paths.
     */
    @Test
    public void testURLPaths() {
        urlAllowList.addEntry("*://*.urbanairship.com/accept.html");
        urlAllowList.addEntry("*://*.urbanairship.com/anythingHTML/*.html");
        urlAllowList.addEntry("https://urbanairship.com/what/index.html");
        urlAllowList.addEntry("wild://cool/*");

        // Reject
        assertFalse(urlAllowList.isAllowed("https://what.urbanairship.com/reject.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("https://what.urbanairship.com/anythingHTML/image.png", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("wile:///whatever", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("wile:///cool", UrlAllowList.SCOPE_ALL));

        // Accept
        assertTrue(urlAllowList.isAllowed("https://what.urbanairship.com/anythingHTML/index.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://what.urbanairship.com/anythingHTML/test.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://what.urbanairship.com/anythingHTML/foo/bar/index.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/what/index.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/what/index.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("wild://cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("wild://cool/", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("wild://cool/path", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test scope.
     */
    @Test
    public void testScope() {
        urlAllowList.addEntry("*://*.urbanairship.com/accept-js.html", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE);
        urlAllowList.addEntry("*://*.urbanairship.com/accept-url.html", UrlAllowList.SCOPE_OPEN_URL);
        urlAllowList.addEntry("*://*.urbanairship.com/accept-all.html", UrlAllowList.SCOPE_ALL);

        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/accept-js.html", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("https://urbanairship.com/accept-js.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("https://urbanairship.com/accept-js.html", UrlAllowList.SCOPE_OPEN_URL));

        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/accept-url.html", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("https://urbanairship.com/accept-url.html", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("https://urbanairship.com/accept-url.html", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));

        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/accept-all.html", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/accept-all.html", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/accept-all.html", UrlAllowList.SCOPE_OPEN_URL));
    }

    /**
     * Test disabling url scope allowList.
     */
    @Test
    public void testDisableUrlScopeAllowList() {
        assertFalse(urlAllowList.isAllowed("https://someurl.com", UrlAllowList.SCOPE_OPEN_URL));

        urlAllowList.addEntry("*", UrlAllowList.SCOPE_OPEN_URL);
        assertTrue(urlAllowList.isAllowed("https://someurl.com", UrlAllowList.SCOPE_OPEN_URL));
        assertFalse(urlAllowList.isAllowed("https://someurl.com", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE));
        assertFalse(urlAllowList.isAllowed("https://someurl.com", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test SCOPE_ALL url allow check if two separate entries match for both types of scope.
     */
    @Test
    public void testScopeAll() {
        urlAllowList.addEntry("*", UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE);
        urlAllowList.addEntry("*://*.urbanairship.com/all.html", UrlAllowList.SCOPE_OPEN_URL);

        assertTrue(urlAllowList.isAllowed("https://urbanairship.com/all.html", UrlAllowList.SCOPE_ALL));
    }

    /**
     * Test deep links.
     */
    @Test
    public void testDeepLinks() {
        // Test any path and undefined host
        assertTrue(urlAllowList.addEntry("com.urbanairship.one:/*"));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.one://cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.one:cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.one:/cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.one:///cool", UrlAllowList.SCOPE_ALL));

        // Test any host and undefined path
        assertTrue(urlAllowList.addEntry("com.urbanairship.two://*"));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.two:cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.two://cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.two:/cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.two:///cool", UrlAllowList.SCOPE_ALL));

        // Test any host and any path
        assertTrue(urlAllowList.addEntry("com.urbanairship.three://*/*"));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.three:cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.three://cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.three:/cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.three:///cool", UrlAllowList.SCOPE_ALL));

        // Test specific host and path
        assertTrue(urlAllowList.addEntry("com.urbanairship.four://*.cool/whatever/*"));
        assertFalse(urlAllowList.isAllowed("com.urbanairship.four:cool", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("com.urbanairship.four://cool", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("com.urbanairship.four:/cool", UrlAllowList.SCOPE_ALL));
        assertFalse(urlAllowList.isAllowed("com.urbanairship.four:///cool", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.four://whatever.cool/whatever/", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.four://cool/whatever/indeed", UrlAllowList.SCOPE_ALL));
    }

    @Test
    public void testRootPath() {
        assertTrue(urlAllowList.addEntry("com.urbanairship.five:/"));

        assertTrue(urlAllowList.isAllowed("com.urbanairship.five:/", UrlAllowList.SCOPE_ALL));
        assertTrue(urlAllowList.isAllowed("com.urbanairship.five:///", UrlAllowList.SCOPE_ALL));

        assertFalse(urlAllowList.isAllowed("com.urbanairship.five:/cool", UrlAllowList.SCOPE_ALL));
    }

    @Test
    public void testCallback() {
        // set up a simple urlAllowList
        assertTrue(urlAllowList.addEntry("https://*.urbanairship.com"));
        assertTrue(urlAllowList.addEntry("https://*.youtube.com", UrlAllowList.SCOPE_OPEN_URL));

        // URLs to be checked
        String matchingURLToReject = "https://www.youtube.com/watch?v=sYd_-pAfbBw";
        String matchingURLToAccept = "https://device-api.urbanairship.com/api/user";
        String nonMatchingURL = "https://maps.google.com";
        int scope = UrlAllowList.SCOPE_OPEN_URL;

        // test when callback has yet to be enabled
        assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope));
        assertTrue(urlAllowList.isAllowed(matchingURLToReject, scope));
        assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope));

        // Enable urlAllowList callback
        TestUrlAllowListCallback callback = new TestUrlAllowListCallback();
        callback.matchingURLToAccept = matchingURLToAccept;
        callback.matchingURLToReject = matchingURLToReject;
        callback.nonMatchingURL = nonMatchingURL;
        urlAllowList.setUrlAllowListCallback(callback);

        // rejected URL should now fail urlAllowList test, others should be unchanged
        assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope));
        assertFalse(urlAllowList.isAllowed(matchingURLToReject, scope));
        assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope));

        // Disable urlAllowList callback
        urlAllowList.setUrlAllowListCallback(null);

        // Should go back to original state when callback was off
        assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope));
        assertTrue(urlAllowList.isAllowed(matchingURLToReject, scope));
        assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope));
    }

    /**
     * Test sms wild card in the path
     */
    @Test
    public void testSmsPath() {
        urlAllowList.addEntry("sms:86753*9*");

        // Reject
        assertFalse(urlAllowList.isAllowed("sms:86753"));
        assertFalse(urlAllowList.isAllowed("sms:867530"));

        // Accept
        assertTrue(urlAllowList.isAllowed("sms:86753191"));
        assertTrue(urlAllowList.isAllowed("sms:8675309"));
    }

    private class TestUrlAllowListCallback implements UrlAllowList.OnUrlAllowListCallback {

        public String matchingURLToAccept;
        public String matchingURLToReject;
        public String nonMatchingURL;

        @Override
        public boolean allowUrl(@NonNull String url, @UrlAllowList.Scope int scope) {
            if (url.equals(matchingURLToAccept)) {
                return true;
            } else if (url.equals(matchingURLToReject)) {
                return false;
            } else if (url.equals(nonMatchingURL)) {
                fail("Callback should not be called when URL is not allowed");
                return false;
            } else {
                fail("Unknown error");
                return false;
            }
        }
    }
}
