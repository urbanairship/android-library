/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.urbanairship.js;

import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Defines a set of URL patterns to match a URL.
 */
public class Whitelist {

    /**
     * Regular expression to match the scheme.
     * <scheme> := '*' | 'http' | 'https'
     */
    private static final String SCHEME_REGEX = "((\\*)|(http)|(https))";

    /**
     * Regular expression to match the host.
     * <host> := '*' | '*.'<any char except '/' and '*'> | <any char except '/' and '*'>
     */
    private static final String HOST_REGEX = "((\\*)|(\\*\\.[^/\\*]+)|([^/\\*]+))";

    /**
     * Regular expression to match the path.
     * <path> := '/' <any chars>
     */
    private static final String PATH_REGEX = "(/.*)";

    /**
     * Regular expression to match the pattern.
     * <pattern> := '*' | <scheme>://<host><path> | <scheme>://<host> | file://<path>
     */
    private static final String PATTERN_REGEX = String.format(Locale.US, "^((\\*)|((%s://%s%s)|(%s://%s)|(file://%s)))",
            SCHEME_REGEX, HOST_REGEX, PATH_REGEX, SCHEME_REGEX, HOST_REGEX, PATH_REGEX);

    /**
     * Regular expression characters. Used to escape any regular expression from the path and host.
     */
    private static final String REGEX_SPECIAL_CHARACTERS = "\\.[]{}()^$?+|*";

    /**
     * Compiled pattern to validate url pattern entries.
     */
    private static final Pattern VALID_PATTERN = Pattern.compile(PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    private List<UriPattern> uriPatterns = new ArrayList<>();

    /**
     * Adds an entry to the whitelist for URL matching. Patterns must be defined with the following
     * syntax:
     * <pre>
     * {@code
     * <pattern> := '*' | <scheme>'://'<host><path> | <scheme>'://'<host> | 'file://'<path>
     * <scheme> := '*' | 'http' | 'https'
     * <host> := '*' | '*.'<any char except '/' and '*'>+ | <any char except '/' and '*'>+
     * <path> := '/'<any char>
     * }
     *
     * Examples:
     *
     *  '*' will match any file, http, or https URL.
     *  '*://www.urbanairship.com' will match any file, http, or https URL from www.urbanairship.com
     *  'https://*.urbanairship.com' will match any https URL from urbanairship.com and any of its subdomains.
     *  'file:///android_asset/*' will match any file in the android assets directory.
     *  'http://urbanairship.com/foo/*.html' will match any url from urbanairship.com that ends in .html
     *  and the path starts with /foo/.
     *
     * </pre>
     *
     * Note: International domains should add an entry for both the ASCII and the unicode versions of
     * the domain.
     *
     * @param pattern The URL pattern to add as a whitelist matcher.
     * @return <code>true</code> if the pattern was added successfully, <code>false</code> if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    public boolean addEntry(String pattern) {
        if (pattern == null || !VALID_PATTERN.matcher(pattern).matches()) {
            Logger.warn("Invalid whitelist pattern " + pattern);
            return false;
        }

        // If we have just a wild card, we need to add a special pattern for both file and https/http
        // URIs.
        if (pattern.equals("*")) {
            uriPatterns.add(new UriPattern(Pattern.compile("(http)|(https)"), null, null));
            uriPatterns.add(new UriPattern(Pattern.compile("file"), null, Pattern.compile("/.*")));
            return true;
        }

        Uri uri = Uri.parse(pattern);
        String scheme = uri.getScheme();
        String host = uri.getEncodedAuthority();
        String path = uri.getPath();

        Pattern schemePattern;
        if (UAStringUtil.isEmpty(scheme) || scheme.equals("*")) {
            schemePattern = Pattern.compile("(http)|(https)");
        } else {
            schemePattern = Pattern.compile(scheme);
        }

        Pattern hostPattern;
        if (UAStringUtil.isEmpty(host) || host.equals("*")) {
            hostPattern = null;
        } else if (host.startsWith("*.")) {
            hostPattern = Pattern.compile("(.*\\.)?" + escapeRegEx(host.substring(2), true));
        } else {
            hostPattern = Pattern.compile(escapeRegEx(host, true));
        }

        Pattern pathPattern;
        if (UAStringUtil.isEmpty(path)) {
            pathPattern = null;
        } else {
            pathPattern = Pattern.compile(escapeRegEx(path, false));
        }

        uriPatterns.add(new UriPattern(schemePattern, hostPattern, pathPattern));
        return true;
    }


    /**
     * Checks if a given URL is whitelisted or not.
     *
     * @param url The URL.
     * @return <code>true</code> If the URL matches any entries in the whitelist.
     */
    public boolean isWhitelisted(String url) {
        if (url == null) {
            return false;
        }

        Uri uri = Uri.parse(url);

        for (UriPattern pattern : uriPatterns) {
            if (pattern.matches(uri)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper method to escape any regular expression.
     *
     * @param input The input to escape.
     * @param escapeWildCards If wild cards '*' should be turned into '.*' or escape
     * @return The input with any regular expression escaped.
     */
    private String escapeRegEx(String input, boolean escapeWildCards) {

        StringBuilder escapedInput = new StringBuilder();

        for (char c : input.toCharArray()) {
            String character = String.valueOf(c);

            if (!escapeWildCards && character.equals("*")) {
                if (character.equals("*")) {
                    escapedInput.append(".");
                }
            } else if (REGEX_SPECIAL_CHARACTERS.contains(character)) {
                escapedInput.append("\\");
            }

            escapedInput.append(character);
        }

        return escapedInput.toString();
    }

    /**
     * Factory method to create the default whitelist with values from the airship config.
     *
     * @param airshipConfigOptions The airship config options.
     * @return The default whitelist.
     * @hide
     */
    public static Whitelist createDefaultWhitelist(AirshipConfigOptions airshipConfigOptions) {
        Whitelist whitelist = new Whitelist();
        whitelist.addEntry("https://*.urbanairship.com");
        if (airshipConfigOptions.whitelist != null) {
            for (String entry : airshipConfigOptions.whitelist) {
                whitelist.addEntry(entry);
            }
        }

        return whitelist;
    }

    /**
     * Helper class that does the actual matching using the scheme and host patterns.
     */
    private class UriPattern {
        private Pattern scheme;
        private Pattern host;
        private Pattern path;

        /**
         * Creates a new UriPattern.
         *
         * @param scheme The pattern to use for scheme matching.
         * @param host The pattern to use for host matching.
         * @param path THe pattern to use for path matching.
         */
        UriPattern(Pattern scheme, Pattern host, Pattern path) {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
        }


        /**
         * Checks if a uri matches the pattern.
         *
         * @param uri The uri to match.
         * @return <code>true</code> if the uri matches, otherwise <code>false</code>.
         */
        boolean matches(Uri uri) {
            if (scheme != null && (uri.getScheme() == null || !scheme.matcher(uri.getScheme()).matches())) {
                return false;
            }

            if (host != null && (uri.getHost() == null || !host.matcher(uri.getHost()).matches())) {
                return false;
            }

            if (path != null && (uri.getPath() == null || !path.matcher(uri.getPath()).matches())) {
                return false;
            }

            return true;
        }
    }
}
