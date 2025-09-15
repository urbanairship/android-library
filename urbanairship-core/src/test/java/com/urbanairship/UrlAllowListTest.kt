/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.UrlAllowList.Companion.createDefaultUrlAllowList
import com.urbanairship.UrlAllowList.OnUrlAllowListCallback
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UrlAllowListTest {

    private var urlAllowList = UrlAllowList()

    /**
     * Test an empty urlAllowList rejects all URLs.
     */
    @Test
    public fun testEmptyUrlAllowList() {
        Assert.assertFalse(urlAllowList.isAllowed(null, UrlAllowList.Scope.JAVASCRIPT_INTERFACE))
        Assert.assertFalse(urlAllowList.isAllowed("", UrlAllowList.Scope.OPEN_URL))
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "urbanairship.com", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "www.urbanairship.com", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://www.urbanairship.com", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com/what", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertFalse(urlAllowList.isAllowed("file:///*", UrlAllowList.Scope.OPEN_URL))
    }

    @Test
    public fun testDefaultAllowListAnyOpen() {
        val airshipConfigOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .build()

        val urlAllowList = createDefaultUrlAllowList(airshipConfigOptions)

        // Messages
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/",
                UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/",
                UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "sms:+18675309?body=Hi%20you", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("sms:8675309", UrlAllowList.Scope.OPEN_URL))
        Assert.assertTrue(urlAllowList.isAllowed("tel:+18675309", UrlAllowList.Scope.OPEN_URL))
        Assert.assertTrue(urlAllowList.isAllowed("tel:867-5309", UrlAllowList.Scope.OPEN_URL))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "mailto:name@example.com?subject=The%20subject%20of%20the%20mail",
                UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "mailto:name@example.com", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://some-random-url.com", UrlAllowList.Scope.OPEN_URL
            )
        )
    }

    /**
     * Test the default urlAllowList accepts Airship URLs.
     */
    @Test
    public fun testDefaultUrlAllowList() {
        val airshipConfigOptions = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setUrlAllowListScopeOpenUrl(null)
            .build()

        val urlAllowList = createDefaultUrlAllowList(airshipConfigOptions)

        // Messages
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/",
                UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://device-api.urbanairship.com/api/user/", UrlAllowList.Scope.ALL
            )
        )

        // Starbucks
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/",
                UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://sbux-dl.urbanairship.com/binary/token/", UrlAllowList.Scope.ALL
            )
        )

        // EU
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://dl.asnapieu.com/binary/token/", UrlAllowList.Scope.ALL
            )
        )

        // sms
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "sms:+18675309?body=Hi%20you", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("sms:8675309", UrlAllowList.Scope.OPEN_URL))

        // tel
        Assert.assertTrue(urlAllowList.isAllowed("tel:+18675309", UrlAllowList.Scope.OPEN_URL))
        Assert.assertTrue(urlAllowList.isAllowed("tel:867-5309", UrlAllowList.Scope.OPEN_URL))

        // email
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "mailto:name@example.com?subject=The%20subject%20of%20the%20mail",
                UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "mailto:name@example.com", UrlAllowList.Scope.OPEN_URL
            )
        )

        // Others are denied
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://some-random-url.com", UrlAllowList.Scope.OPEN_URL
            )
        )
    }

    /**
     * Test setting invalid patterns returns false.
     */
    @Test
    public fun testInvalidPatterns() {
        // Not a URL
        Assert.assertFalse(urlAllowList.addEntry("not a url"))

        // Missing schemes
        Assert.assertFalse(urlAllowList.addEntry("www.urbanairship.com"))
        Assert.assertFalse(urlAllowList.addEntry("://www.urbanairship.com"))

        // White space in scheme
        Assert.assertFalse(urlAllowList.addEntry(" file://*"))

        // Invalid hosts
        Assert.assertFalse(urlAllowList.addEntry("*://what*"))
        Assert.assertFalse(urlAllowList.addEntry("*://*what"))
    }

    /**
     * Test international URLs.
     */
    @Test
    public fun testInternationalURLs() {
        Assert.assertTrue(urlAllowList.addEntry("*://ουτοπία.δπθ.gr"))
        Assert.assertTrue(urlAllowList.addEntry("*://müller.com"))

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://ουτοπία.δπθ.gr", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("https://müller.com", UrlAllowList.Scope.ALL))
    }

    /**
     * Test wild card scheme with wild cards.
     */
    @Test
    public fun testSchemeWildCard() {
        Assert.assertTrue(urlAllowList.addEntry("*://www.urbanairship.com"))
        Assert.assertTrue(urlAllowList.addEntry("cool*story://rad"))

        // Reject
        Assert.assertFalse(urlAllowList.isAllowed(null, UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("urbanairship.com", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("www.urbanairship.com", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("cool://rad", UrlAllowList.Scope.ALL))

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "file://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "valid://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("cool----story://rad", UrlAllowList.Scope.ALL))
        Assert.assertTrue(urlAllowList.isAllowed("coolstory://rad", UrlAllowList.Scope.ALL))
    }

    /**
     * Test scheme matching works.
     */
    @Test
    public fun testScheme() {
        urlAllowList.addEntry("https://www.urbanairship.com")
        urlAllowList.addEntry("file:///asset.html")

        // Reject
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("file:///asset.html", UrlAllowList.Scope.ALL))
    }

    /**
     * Test regular expression on the host and schema are treated as literals.
     */
    @Test
    public fun testRegExEscaped() {
        Assert.assertTrue(urlAllowList.addEntry("w+.+://[a-z,A-Z]+"))

        Assert.assertFalse(urlAllowList.isAllowed("wwww://urbanairship", UrlAllowList.Scope.ALL))

        // It should match on a host that is equal to [a-z,A-Z]+
        Assert.assertTrue(urlAllowList.isAllowed("w+.+://[a-z,A-Z]%2B", UrlAllowList.Scope.ALL))
    }

    /**
     * Test host matching actually works.
     */
    @Test
    public fun testHost() {
        Assert.assertTrue(
            urlAllowList.addEntry(
                "http://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.addEntry("http://oh.hi.marc", UrlAllowList.Scope.ALL))

        // Reject
        Assert.assertFalse(urlAllowList.isAllowed("http://oh.bye.marc", UrlAllowList.Scope.ALL))
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com.hackers.io", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://omg.www.urbanairship.com.hackers.io", UrlAllowList.Scope.ALL
            )
        )

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://www.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("http://oh.hi.marc", UrlAllowList.Scope.ALL))
    }

    /**
     * Test wild card in the host.
     */
    @Test
    public fun testHostWildCard() {
        Assert.assertTrue(urlAllowList.addEntry("http://*", UrlAllowList.Scope.ALL))
        Assert.assertTrue(urlAllowList.addEntry("https://*.coolstory", UrlAllowList.Scope.ALL))

        // * is only available at the beginning
        Assert.assertFalse(urlAllowList.addEntry("https://*.coolstory.*", UrlAllowList.Scope.ALL))

        // Reject
        Assert.assertFalse(urlAllowList.isAllowed(null, UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("https://cool", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("https://story", UrlAllowList.Scope.ALL))

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://what.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http:///android-asset/test.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://www.anything.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("https://coolstory", UrlAllowList.Scope.ALL))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://what.coolstory", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://what.what.coolstory", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test wild card for subdomain accepts any subdomain, including no subdomain.
     */
    @Test
    public fun testHostWildCardSubDomain() {
        Assert.assertTrue(urlAllowList.addEntry("http://*.urbanairship.com"))

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://what.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://hi.urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://urbanairship.com", UrlAllowList.Scope.ALL
            )
        )

        // Reject
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "http://lololurbanairship.com", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test wild card matcher matches any url.
     */
    @Test
    public fun testWildCardMatcher() {
        Assert.assertTrue(urlAllowList.addEntry("*"))

        Assert.assertTrue(urlAllowList.isAllowed("file:///what/oh/hi", UrlAllowList.Scope.ALL))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://hi.urbanairship.com/path", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "http://urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "cool.story://urbanairship.com", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "sms:+18664504185?body=Hi", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test file paths.
     */
    @Test
    public fun testFilePaths() {
        Assert.assertTrue(urlAllowList.addEntry("file:///foo/index.html", UrlAllowList.Scope.ALL))

        // Reject
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "file:///foo/test.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "file:///foo/bar/index.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "file:///foooooooo/index.html", UrlAllowList.Scope.ALL
            )
        )

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "file:///foo/index.html", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test file paths with wild cards.
     */
    @Test
    public fun testFilePathsWildCard() {
        Assert.assertTrue(urlAllowList.addEntry("file:///foo/*"))

        // Reject
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "file:///bar/index.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(urlAllowList.isAllowed("", UrlAllowList.Scope.ALL))

        // Accept
        Assert.assertTrue(urlAllowList.isAllowed("file:///foo/test.html", UrlAllowList.Scope.ALL))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "file:///foo/html/index.html", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test paths paths.
     */
    @Test
    public fun testURLPaths() {
        urlAllowList.addEntry("*://*.urbanairship.com/accept.html")
        urlAllowList.addEntry("*://*.urbanairship.com/anythingHTML/*.html")
        urlAllowList.addEntry("https://urbanairship.com/what/index.html")
        urlAllowList.addEntry("wild://cool/*")

        // Reject
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://what.urbanairship.com/reject.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://what.urbanairship.com/anythingHTML/image.png", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(urlAllowList.isAllowed("wile:///whatever", UrlAllowList.Scope.ALL))
        Assert.assertFalse(urlAllowList.isAllowed("wile:///cool", UrlAllowList.Scope.ALL))

        // Accept
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://what.urbanairship.com/anythingHTML/index.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://what.urbanairship.com/anythingHTML/test.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://what.urbanairship.com/anythingHTML/foo/bar/index.html",
                UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/what/index.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/what/index.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(urlAllowList.isAllowed("wild://cool", UrlAllowList.Scope.ALL))
        Assert.assertTrue(urlAllowList.isAllowed("wild://cool/", UrlAllowList.Scope.ALL))
        Assert.assertTrue(urlAllowList.isAllowed("wild://cool/path", UrlAllowList.Scope.ALL))
    }

    /**
     * Test scope.
     */
    @Test
    public fun testScope() {
        urlAllowList.addEntry(
            "*://*.urbanairship.com/accept-js.html", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
        )
        urlAllowList.addEntry(
            "*://*.urbanairship.com/accept-url.html", UrlAllowList.Scope.OPEN_URL
        )
        urlAllowList.addEntry("*://*.urbanairship.com/accept-all.html", UrlAllowList.Scope.ALL)

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-js.html", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-js.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-js.html", UrlAllowList.Scope.OPEN_URL
            )
        )

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-url.html", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-url.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-url.html", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-all.html", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-all.html", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/accept-all.html", UrlAllowList.Scope.OPEN_URL
            )
        )
    }

    /**
     * Test disabling url scope allowList.
     */
    @Test
    public fun testDisableUrlScopeAllowList() {
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://someurl.com", UrlAllowList.Scope.OPEN_URL
            )
        )

        urlAllowList.addEntry("*", UrlAllowList.Scope.OPEN_URL)
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://someurl.com", UrlAllowList.Scope.OPEN_URL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "https://someurl.com", UrlAllowList.Scope.JAVASCRIPT_INTERFACE
            )
        )
        Assert.assertFalse(urlAllowList.isAllowed("https://someurl.com", UrlAllowList.Scope.ALL))
    }

    /**
     * Test Scope.ALL url allow check if two separate entries match for both types of scope.
     */
    @Test
    public fun testScopeAll() {
        urlAllowList.addEntry("*", UrlAllowList.Scope.JAVASCRIPT_INTERFACE)
        urlAllowList.addEntry("*://*.urbanairship.com/all.html", UrlAllowList.Scope.OPEN_URL)

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "https://urbanairship.com/all.html", UrlAllowList.Scope.ALL
            )
        )
    }

    /**
     * Test deep links.
     */
    @Test
    public fun testDeepLinks() {
        // Test any path and undefined host
        Assert.assertTrue(urlAllowList.addEntry("com.urbanairship.one:/*"))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.one://cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.one:cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.one:/cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.one:///cool", UrlAllowList.Scope.ALL
            )
        )

        // Test any host and undefined path
        Assert.assertTrue(urlAllowList.addEntry("com.urbanairship.two://*"))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.two:cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.two://cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.two:/cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.two:///cool", UrlAllowList.Scope.ALL
            )
        )

        // Test any host and any path
        Assert.assertTrue(urlAllowList.addEntry("com.urbanairship.three://*/*"))
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.three:cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.three://cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.three:/cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.three:///cool", UrlAllowList.Scope.ALL
            )
        )

        // Test specific host and path
        Assert.assertTrue(urlAllowList.addEntry("com.urbanairship.four://*.cool/whatever/*"))
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "com.urbanairship.four:cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "com.urbanairship.four://cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "com.urbanairship.four:/cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertFalse(
            urlAllowList.isAllowed(
                "com.urbanairship.four:///cool", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.four://whatever.cool/whatever/", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.four://cool/whatever/indeed", UrlAllowList.Scope.ALL
            )
        )
    }

    @Test
    public fun testRootPath() {
        Assert.assertTrue(urlAllowList.addEntry("com.urbanairship.five:/"))

        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.five:/", UrlAllowList.Scope.ALL
            )
        )
        Assert.assertTrue(
            urlAllowList.isAllowed(
                "com.urbanairship.five:///", UrlAllowList.Scope.ALL
            )
        )

        Assert.assertFalse(
            urlAllowList.isAllowed(
                "com.urbanairship.five:/cool", UrlAllowList.Scope.ALL
            )
        )
    }

    @Test
    public fun testCallback() {
        // set up a simple urlAllowList
        Assert.assertTrue(urlAllowList.addEntry("https://*.urbanairship.com"))
        Assert.assertTrue(
            urlAllowList.addEntry(
                "https://*.youtube.com", UrlAllowList.Scope.OPEN_URL
            )
        )

        // URLs to be checked
        val matchingURLToReject = "https://www.youtube.com/watch?v=sYd_-pAfbBw"
        val matchingURLToAccept = "https://device-api.urbanairship.com/api/user"
        val nonMatchingURL = "https://maps.google.com"
        val scope = UrlAllowList.Scope.OPEN_URL

        // test when callback has yet to be enabled
        Assert.assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope))
        Assert.assertTrue(urlAllowList.isAllowed(matchingURLToReject, scope))
        Assert.assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope))

        // Enable urlAllowList callback
        val callback = TestUrlAllowListCallback()
        callback.matchingURLToAccept = matchingURLToAccept
        callback.matchingURLToReject = matchingURLToReject
        callback.nonMatchingURL = nonMatchingURL
        urlAllowList.setUrlAllowListCallback(callback)

        // rejected URL should now fail urlAllowList test, others should be unchanged
        Assert.assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope))
        Assert.assertFalse(urlAllowList.isAllowed(matchingURLToReject, scope))
        Assert.assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope))

        // Disable urlAllowList callback
        urlAllowList.setUrlAllowListCallback(null)

        // Should go back to original state when callback was off
        Assert.assertTrue(urlAllowList.isAllowed(matchingURLToAccept, scope))
        Assert.assertTrue(urlAllowList.isAllowed(matchingURLToReject, scope))
        Assert.assertFalse(urlAllowList.isAllowed(nonMatchingURL, scope))
    }

    /**
     * Test sms wild card in the path
     */
    @Test
    public fun testSmsPath() {
        urlAllowList.addEntry("sms:86753*9*")

        // Reject
        Assert.assertFalse(urlAllowList.isAllowed("sms:86753"))
        Assert.assertFalse(urlAllowList.isAllowed("sms:867530"))

        // Accept
        Assert.assertTrue(urlAllowList.isAllowed("sms:86753191"))
        Assert.assertTrue(urlAllowList.isAllowed("sms:8675309"))
    }

    private inner class TestUrlAllowListCallback : OnUrlAllowListCallback {

        var matchingURLToAccept: String? = null
        var matchingURLToReject: String? = null
        var nonMatchingURL: String? = null

        override fun allowUrl(url: String, scope: UrlAllowList.Scope): Boolean {
            if (url == matchingURLToAccept) {
                return true
            } else if (url == matchingURLToReject) {
                return false
            } else if (url == nonMatchingURL) {
                Assert.fail("Callback should not be called when URL is not allowed")
                return false
            } else {
                Assert.fail("Unknown error")
                return false
            }
        }
    }
}
