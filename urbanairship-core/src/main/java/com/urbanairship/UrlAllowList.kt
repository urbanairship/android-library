/* Copyright Airship and Contributors */
package com.urbanairship

import android.net.Uri
import androidx.core.util.ObjectsCompat
import java.util.regex.Pattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Defines a set of URL patterns to match a URL.
 */
public class UrlAllowList public constructor() {

    public class Scope(private val rawValue: Int) {

        public fun contains(scope: Scope): Boolean {
            return this.rawValue and scope.rawValue == scope.rawValue
        }

        internal fun combining(scope: Scope): Scope {
            return Scope(this.rawValue or scope.rawValue)
        }

        public companion object {
            internal val NONE: Scope = Scope(rawValue = 0)
            /**
             * Allow list scope for testing URLs before loading them in either
             * a web view or deep linking to them externally from the app.
             */
            public val JAVASCRIPT_INTERFACE: Scope = Scope(1)

            /**
             * UrlAllowList entry applies to both url and JS interface.
             */
            public val OPEN_URL: Scope = Scope(1 shl 1)

            /**
             * Allow list scope for testing web view URLs before injected the
             * Airship JS interface (native bridge).
             */
            public val ALL: Scope = Scope(JAVASCRIPT_INTERFACE.rawValue or OPEN_URL.rawValue)
        }
    }

    /**
     * Interface that defines a callback that can be used to reject or allow a URL.
     */
    public fun interface OnUrlAllowListCallback {

        /**
         * Called when the URL has passed the isAllowed() check.
         *
         * @param url The URL.
         * @param scope The scope.
         * @return `true` to accept the URL, `false` to reject the URL.
         */
        public fun allowUrl(url: String, scope: Scope): Boolean
    }

    private var urlAllowListCallback: OnUrlAllowListCallback? = null

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    /**
     * Adds an entry to the URL allow list for URL matching. Patterns must be defined with the following
     * syntax:
     * ```
     * `<pattern> := '*' | <scheme>'://'<host>/<path> | <scheme>'://'<host> | <scheme>':/'<path> | <scheme>':///'<path>
     * <scheme> := <any char combination, '*' are treated as wild cards>
     * <host> := '*' | '*.'<any char combination except '/' and '*'> | <any char combination except '/' and '*'>
     * <path> := <any char combination, '*' are treated as wild cards>
    ` *
     *
     * Examples:
     *
     * '*' will match any URI
     * '*://www.urbanairship.com' will match any schema from www.urbanairship.com
     * 'https:// *.urbanairship.com' will match any https URL from urbanairship.com and any of its subdomains.
     * 'file:///android_asset/ *' will match any file in the android assets directory.
     * 'http://urbanairship.com/foo/ *.html' will match any url from urbanairship.com that ends in .html
     * and the path starts with /foo/.
     * ```
     *
     *
     * Note: International domains should add entries for both the ASCII and the unicode versions of
     * the domain.
     *
     * @param pattern The URL pattern to add as a URL allow list matcher.
     * @param scope The scope that entry applies to.
     * @return `true` if the pattern was added successfully, `false` if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    @JvmOverloads
    public fun addEntry(pattern: String, scope: Scope = Scope.ALL): Boolean {
        if (pattern == "*") {
            addEntry(UriPattern(null, null, null), scope)
            return true
        }

        val uri = Uri.parse(pattern)
        if (uri == null) {
            UALog.e("Invalid URL allow list pattern %s", pattern)
            return false
        }

        val scheme = uri.scheme
        if (scheme.isNullOrEmpty() || !PATH_OR_SCHEME_PATTERN.matcher(scheme).matches()) {
            UALog.e("Invalid scheme %s in URL allow list pattern %s", scheme, pattern)
            return false
        }

        val host = if (uri.encodedAuthority.isNullOrEmpty()) null else uri.encodedAuthority
        if (host != null && !HOST_PATTERN.matcher(host).matches()) {
            UALog.e("Invalid host %s in URL allow list pattern %s", host, pattern)
            return false
        }

        val path = if (uri.isOpaque) uri.schemeSpecificPart else uri.path
        if (path != null && !PATH_OR_SCHEME_PATTERN.matcher(path).matches()) {
            UALog.e("Invalid path %s in URL allow list pattern %s", path, pattern)
            return false
        }
        val schemePattern = if (scheme.isEmpty() || scheme == "*") {
            null
        } else {
            Pattern.compile(escapeRegEx(scheme, false))
        }
        val hostPattern = if (host.isNullOrEmpty() || host == "*") {
            null
        } else if (host.startsWith("*.")) {
            Pattern.compile("(.*\\.)?" + escapeRegEx(host.substring(2), true))
        } else {
            Pattern.compile(escapeRegEx(host, true))
        }
        val pathPattern = if (path.isNullOrEmpty() || path == "/*") {
            null
        } else {
            Pattern.compile(escapeRegEx(path, false))
        }

        addEntry(UriPattern(schemePattern, hostPattern, pathPattern), scope)
        return true
    }

    /**
     * Adds an entry.
     *
     * @param pattern The pattern.
     * @param scope The scope.
     */
    private fun addEntry(pattern: UriPattern, scope: Scope) {
        entries.update {
            it.toMutableList()
                .also { it.add(Entry(pattern, scope)) }
                .toList()
        }
    }

    /**
     * Checks if a given URL is allowed or not with scope [Scope.ALL].
     *
     * @param url The URL.
     * @return `true` If the URL matches any entries in the URL allow list.
     */
    public fun isAllowed(url: String?): Boolean {
        return isAllowed(url, Scope.ALL)
    }

    /**
     * Checks if a given URL is allowed or not.
     *
     * @param url The URL.
     * @param scope The scope.
     * @return `true` If the URL matches any entries in the URL allow list.
     */
    public fun isAllowed(url: String?, scope: Scope): Boolean {
        if (url == null) {
            return false
        }

        val uri = Uri.parse(url)
        var matchedScope = Scope.NONE

        entries.value
            .filter { it.pattern.matches(uri) }
            .forEach { matchedScope = matchedScope.combining(it.scope) }

        var match = matchedScope.contains(scope)

        // if the url is allowed, allow the app to reject the url
        if (match) {
            urlAllowListCallback?.let { match = it.allowUrl(url, scope) }
        }

        return match
    }

    /**
     * Helper method to escape any regular expression.
     *
     * @param input The input to escape.
     * @param escapeWildCards If wild cards '*' should be turned into '.*' or escape
     * @return The input with any regular expression escaped.
     */
    private fun escapeRegEx(input: String, escapeWildCards: Boolean): String {
        val escapedInput = StringBuilder()

        for (c in input.toCharArray()) {
            val character = c.toString()

            if (!escapeWildCards && character == "*") {
                escapedInput.append(".")
            } else if (REGEX_SPECIAL_CHARACTERS.contains(character)) {
                escapedInput.append("\\")
            }

            escapedInput.append(character)
        }

        return escapedInput.toString()
    }

    /**
     * Sets the urlAllowList callback.
     *
     * @param urlAllowListCallback The urlAllowList callback.
     */
    public fun setUrlAllowListCallback(urlAllowListCallback: OnUrlAllowListCallback?) {
        this.urlAllowListCallback = urlAllowListCallback
    }

    /**
     * Helper class that does the actual matching using the scheme and host patterns.
     */
    private class UriPattern(
        private val scheme: Pattern?,
        private val host: Pattern?,
        private val path: Pattern?
    ) {

        /**
         * Checks if a uri matches the pattern.
         *
         * @param uri The uri to match.
         * @return `true` if the uri matches, otherwise `false`.
         */
        fun matches(uri: Uri): Boolean {
            if (scheme != null && (uri.scheme == null || !scheme.matcher(uri.scheme).matches())) {
                return false
            }

            if (host != null && (uri.host == null || !host.matcher(uri.host).matches())) {
                return false
            }

            val uriPath = if (uri.isOpaque) uri.schemeSpecificPart else uri.path
            return path == null || (uriPath != null && path.matcher(uriPath).matches())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }

            val that = other as UriPattern

            if (scheme != that.scheme) return false
            if (host != that.host) return false
            if (path != that.path) return false

            return true
        }

        override fun hashCode(): Int {
            return ObjectsCompat.hash(scheme, host, path)
        }
    }

    private data class Entry(val pattern: UriPattern, val scope: Scope)

    public companion object {

        /**
         * Regular expression to match the host.
         * <host> := '*' | *.<valid host characters> | <valid host characters>
         */
        private val HOST_PATTERN: Pattern =
            Pattern.compile("((\\*)|(\\*\\.[^/\\*]+)|([^/\\*]+))", Pattern.CASE_INSENSITIVE)

        /**
         * Regular expression to match the path or scheme.
         * <path> := <any chars (no spaces), `*` will match 0 or more characters>
         */
        private val PATH_OR_SCHEME_PATTERN: Pattern =
            Pattern.compile("([^\\s]*)", Pattern.CASE_INSENSITIVE)

        /**
         * Regular expression characters. Used to escape any regular expression from the path and host.
         */
        private const val REGEX_SPECIAL_CHARACTERS = "\\.[]{}()^$?+|*"

        /**
         * Factory method to create the default URL allow list with values from the airship config.
         *
         * @param airshipConfigOptions The airship config options.
         * @return The default URL allow list.
         * @hide
         */
        @JvmStatic
        public fun createDefaultUrlAllowList(airshipConfigOptions: AirshipConfigOptions): UrlAllowList {
            val urlAllowList = UrlAllowList()
            urlAllowList.addEntry("https://*.urbanairship.com")
            urlAllowList.addEntry("https://*.asnapieu.com")
            urlAllowList.addEntry("sms:", Scope.OPEN_URL)
            urlAllowList.addEntry("mailto:", Scope.OPEN_URL)
            urlAllowList.addEntry("tel:", Scope.OPEN_URL)

            if (!airshipConfigOptions.isAllowListSet && !airshipConfigOptions.isAllowListScopeOpenSet) {
                UALog.e(
                    "The Airship config options is missing URL allow list rules for SCOPE_OPEN " + "that controls what external URLs are able to be opened externally or loaded " + "in a web view by Airship. By default, all URLs will be allowed. " + "To suppress this error, specify the config urlAllowListScopeOpenUrl = [*] " + "to keep the defaults, or by providing a list of rules that your app expects. " + "See https://docs.airship.com/platform/mobile/setup/sdk/android/#url-allow-list " + "for more information."
                )
                urlAllowList.addEntry("*", Scope.OPEN_URL)
            }

            for (entry in airshipConfigOptions.urlAllowList) {
                urlAllowList.addEntry(entry, Scope.ALL)
            }

            for (entry in airshipConfigOptions.urlAllowListScopeJavaScriptInterface) {
                urlAllowList.addEntry(entry, Scope.JAVASCRIPT_INTERFACE)
            }

            for (entry in airshipConfigOptions.urlAllowListScopeOpenUrl) {
                urlAllowList.addEntry(entry, Scope.OPEN_URL)
            }

            return urlAllowList
        }
    }
}
