/* Copyright Airship and Contributors */

package com.urbanairship.js;

import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Defines a set of URL patterns to match a URL.
 */
public class UrlAllowList {

    /**
     * UrlAllowList entry applies to JS interface.
     */
    public static final int SCOPE_JAVASCRIPT_INTERFACE = 1;

    /**
     * UrlAllowList entry applies to any url handling.
     */
    public static final int SCOPE_OPEN_URL = 1 << 1;

    /**
     * UrlAllowList entry applies to both url and JS interface.
     */
    public static final int SCOPE_ALL = SCOPE_JAVASCRIPT_INTERFACE | SCOPE_OPEN_URL;

    @IntDef(flag = true, value = {SCOPE_JAVASCRIPT_INTERFACE, SCOPE_OPEN_URL, SCOPE_ALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scope {
    }

    /**
     * Regular expression to match the host.
     * <host> := '*' | *.<valid host characters> | <valid host characters>
     */
    private static final Pattern HOST_PATTERN = Pattern.compile("((\\*)|(\\*\\.[^/\\*]+)|([^/\\*]+))", Pattern.CASE_INSENSITIVE);

    /**
     * Regular expression to match the path or scheme.
     * <path> := <any chars (no spaces), `*` will match 0 or more characters>
     */
    private static final Pattern PATH_OR_SCHEME_PATTERN = Pattern.compile("([^\\s]*)", Pattern.CASE_INSENSITIVE);

    /**
     * Regular expression characters. Used to escape any regular expression from the path and host.
     */
    private static final String REGEX_SPECIAL_CHARACTERS = "\\.[]{}()^$?+|*";

    /**
     * Interface that defines a callback that can be used to reject or allow a URL.
     */
    public interface OnUrlAllowListCallback {

        /**
         * Called when the URL has passed the isAllowed() check.
         *
         * @param url The URL.
         * @param scope The scope.
         * @return <code>true</code> to accept the URL, <code>false</code> to reject the URL.
         */
        boolean allowUrl(@NonNull String url, @Scope int scope);

    }

    @Nullable
    private OnUrlAllowListCallback urlAllowListCallback;

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Adds an entry to the URL allow list for URL matching. Patterns must be defined with the following
     * syntax:
     * <pre>
     * {@code
     * <pattern> := '*' | <scheme>'://'<host>/<path> | <scheme>'://'<host> | <scheme>':/'<path> | <scheme>':///'<path>  | <scheme>':'<path>
     * <scheme> := <any char combination ' ', '*' are treated as wild cards>
     * <host> := '*' | '*.'<any char combination except ' ', '/' and '*'> | <any char combination except ' ', '/', and '*'>
     * <path> := <any char combination except ' ', '*' are treated as wild cards>
     * }
     *
     * Examples:
     *
     *  '*' will match any URI
     *  '*://www.urbanairship.com' will match any schema from www.urbanairship.com
     *  'https://*.urbanairship.com' will match any https URL from urbanairship.com and any of its subdomains.
     *  'file:///android_asset/*' will match any file in the android assets directory.
     *  'http://urbanairship.com/foo/*.html' will match any url from urbanairship.com that ends in .html
     *  and the path starts with /foo/.
     *
     * </pre>
     * <p>
     * Note: International domains should add entries for both the ASCII and the unicode versions of
     * the domain.
     *
     * @param pattern The URL pattern to add as a URL allow list matcher.
     * @return <code>true</code> if the pattern was added successfully, <code>false</code> if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    public boolean addEntry(@NonNull String pattern) {
        return addEntry(pattern, SCOPE_ALL);
    }

    /**
     * Adds an entry to the URL allow list for URL matching. Patterns must be defined with the following
     * syntax:
     * <pre>
     * {@code
     * <pattern> := '*' | <scheme>'://'<host>/<path> | <scheme>'://'<host> | <scheme>':/'<path> | <scheme>':///'<path>
     * <scheme> := <any char combination, '*' are treated as wild cards>
     * <host> := '*' | '*.'<any char combination except '/' and '*'> | <any char combination except '/' and '*'>
     * <path> := <any char combination, '*' are treated as wild cards>
     * }
     *
     * Examples:
     *
     *  '*' will match any URI
     *  '*://www.urbanairship.com' will match any schema from www.urbanairship.com
     *  'https://*.urbanairship.com' will match any https URL from urbanairship.com and any of its subdomains.
     *  'file:///android_asset/*' will match any file in the android assets directory.
     *  'http://urbanairship.com/foo/*.html' will match any url from urbanairship.com that ends in .html
     *  and the path starts with /foo/.
     *
     * </pre>
     * <p>
     * Note: International domains should add entries for both the ASCII and the unicode versions of
     * the domain.
     *
     * @param pattern The URL pattern to add as a URL allow list matcher.
     * @param scope The scope that entry applies to.
     * @return <code>true</code> if the pattern was added successfully, <code>false</code> if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    public boolean addEntry(@NonNull String pattern, @Scope int scope) {
        if (pattern.equals("*")) {
            addEntry(new UriPattern(null, null, null), scope);
            return true;
        }

        Uri uri = Uri.parse(pattern);
        if (uri == null) {
            Logger.error("Invalid URL allow list pattern %s", pattern);
            return false;
        }

        String scheme = uri.getScheme();
        if (UAStringUtil.isEmpty(scheme) || !PATH_OR_SCHEME_PATTERN.matcher(scheme).matches()) {
            Logger.error("Invalid scheme %s in URL allow list pattern %s", scheme, pattern);
            return false;
        }

        String host = UAStringUtil.nullIfEmpty(uri.getEncodedAuthority());
        if (host != null && !HOST_PATTERN.matcher(host).matches()) {
            Logger.error("Invalid host %s in URL allow list pattern %s", host, pattern);
            return false;
        }

        String path = uri.isOpaque() ? uri.getSchemeSpecificPart() : uri.getPath();
        if (path != null && !PATH_OR_SCHEME_PATTERN.matcher(path).matches()) {
            Logger.error("Invalid path %s in URL allow list pattern %s", path, pattern);
            return false;
        }

        Pattern schemePattern;
        if (UAStringUtil.isEmpty(scheme) || scheme.equals("*")) {
            schemePattern = null;
        } else {
            schemePattern = Pattern.compile(escapeRegEx(scheme, false));
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
        if (UAStringUtil.isEmpty(path) || path.equals("/*")) {
            pathPattern = null;
        } else {
            pathPattern = Pattern.compile(escapeRegEx(path, false));
        }

        addEntry(new UriPattern(schemePattern, hostPattern, pathPattern), scope);
        return true;
    }

    /**
     * Adds an entry.
     *
     * @param pattern The pattern.
     * @param scope The scope.
     */
    private void addEntry(@NonNull UriPattern pattern, @Scope int scope) {
        synchronized (entries) {
            entries.add(new Entry(pattern, scope));
        }
    }

    /**
     * Checks if a given URL is allowed or not with scope {@link #SCOPE_ALL}.
     *
     * @param url The URL.
     * @return <code>true</code> If the URL matches any entries in the URL allow list.
     */
    public boolean isAllowed(@Nullable String url) {
        return isAllowed(url, SCOPE_ALL);
    }

    /**
     * Checks if a given URL is allowed or not.
     *
     * @param url The URL.
     * @param scope The scope.
     * @return <code>true</code> If the URL matches any entries in the URL allow list.
     */
    public boolean isAllowed(@Nullable String url, @Scope int scope) {
        if (url == null) {
            return false;
        }

        Uri uri = Uri.parse(url);
        int matchedScope = 0;

        synchronized (entries) {
            for (Entry entry : entries) {
                if (entry.pattern.matches(uri)) {
                    matchedScope |= entry.scope;
                }
            }
        }

        boolean match = ((matchedScope & scope) == scope);

        // if the url is allowed, allow the app to reject the url
        if (match && (urlAllowListCallback != null)) {
            match = urlAllowListCallback.allowUrl(url, scope);
        }

        return match;
    }

    /**
     * Helper method to escape any regular expression.
     *
     * @param input The input to escape.
     * @param escapeWildCards If wild cards '*' should be turned into '.*' or escape
     * @return The input with any regular expression escaped.
     */
    private String escapeRegEx(@NonNull String input, boolean escapeWildCards) {

        StringBuilder escapedInput = new StringBuilder();

        for (char c : input.toCharArray()) {
            String character = String.valueOf(c);

            if (!escapeWildCards && character.equals("*")) {
                escapedInput.append(".");
            } else if (REGEX_SPECIAL_CHARACTERS.contains(character)) {
                escapedInput.append("\\");
            }

            escapedInput.append(character);
        }

        return escapedInput.toString();
    }

    /**
     * Factory method to create the default URL allow list with values from the airship config.
     *
     * @param airshipConfigOptions The airship config options.
     * @return The default URL allow list.
     * @hide
     */
    @NonNull
    public static UrlAllowList createDefaultUrlAllowList(@NonNull AirshipConfigOptions airshipConfigOptions) {
        UrlAllowList urlAllowList = new UrlAllowList();
        urlAllowList.addEntry("https://*.urbanairship.com");
        urlAllowList.addEntry("https://*.youtube.com", SCOPE_OPEN_URL);
        urlAllowList.addEntry("https://*.asnapieu.com");
        urlAllowList.addEntry("sms:", SCOPE_OPEN_URL);
        urlAllowList.addEntry("mailto:", SCOPE_OPEN_URL);
        urlAllowList.addEntry("tel:", SCOPE_OPEN_URL);

        for (String entry : airshipConfigOptions.urlAllowList) {
            urlAllowList.addEntry(entry, SCOPE_ALL);
        }
        for (String entry : airshipConfigOptions.urlAllowListScopeJavaScriptInterface) {
            urlAllowList.addEntry(entry, SCOPE_JAVASCRIPT_INTERFACE);
        }
        for (String entry : airshipConfigOptions.urlAllowListScopeOpenUrl) {
            urlAllowList.addEntry(entry, SCOPE_OPEN_URL);
        }

        return urlAllowList;
    }

    /**
     * Sets the urlAllowList callback.
     *
     * @param urlAllowListCallback The urlAllowList callback.
     */
    public void setUrlAllowListCallback(@Nullable OnUrlAllowListCallback urlAllowListCallback) {
        this.urlAllowListCallback = urlAllowListCallback;
    }

    /**
     * Helper class that does the actual matching using the scheme and host patterns.
     */
    private static class UriPattern {

        private final Pattern scheme;
        private final Pattern host;
        private final Pattern path;

        /**
         * Creates a new UriPattern.
         *
         * @param scheme The pattern to use for scheme matching.
         * @param host The pattern to use for host matching.
         * @param path THe pattern to use for path matching.
         */
        UriPattern(@Nullable Pattern scheme, @Nullable Pattern host, @Nullable Pattern path) {
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
        boolean matches(@NonNull Uri uri) {
            if (scheme != null && (uri.getScheme() == null || !scheme.matcher(uri.getScheme()).matches())) {
                return false;
            }

            if (host != null && (uri.getHost() == null || !host.matcher(uri.getHost()).matches())) {
                return false;
            }

            String uriPath = uri.isOpaque() ? uri.getSchemeSpecificPart() : uri.getPath();
            return path == null || (uriPath != null && path.matcher(uriPath).matches());
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UriPattern that = (UriPattern) o;

            if (scheme != null ? !scheme.equals(that.scheme) : that.scheme != null) {
                return false;
            }

            if (host != null ? !host.equals(that.host) : that.host != null) {
                return false;
            }

            return path != null ? path.equals(that.path) : that.path == null;
        }

        @Override
        public int hashCode() {
            int result = scheme != null ? scheme.hashCode() : 0;
            result = 31 * result + (host != null ? host.hashCode() : 0);
            result = 31 * result + (path != null ? path.hashCode() : 0);
            return result;
        }

    }

    private static class Entry {

        private final int scope;
        private final UriPattern pattern;

        private Entry(UriPattern pattern, @Scope int scope) {
            this.scope = scope;
            this.pattern = pattern;
        }

    }

}
