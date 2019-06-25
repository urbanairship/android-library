/* Copyright Airship and Contributors */

package com.urbanairship.js;

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Defines a set of URL patterns to match a URL.
 */
public class Whitelist {

    /**
     * Whitelist entry applies to JS interface.
     */
    public static final int SCOPE_JAVASCRIPT_INTERFACE = 1;

    /**
     * Whitelist entry applies to any url handling.
     */
    public static final int SCOPE_OPEN_URL = 1 << 1;

    /**
     * Whitelist entry applies to both url and JS interface.
     */
    public static final int SCOPE_ALL = SCOPE_JAVASCRIPT_INTERFACE | SCOPE_OPEN_URL;

    @IntDef(flag = true, value = { SCOPE_JAVASCRIPT_INTERFACE, SCOPE_OPEN_URL, SCOPE_ALL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scope {}

    /**
     * Regular expression to match the scheme.
     * <scheme> := '*' | <valid scheme characters, `*` will match 0 or more characters>
     */
    private static final String SCHEME_REGEX = "([^\\s]+)";

    /**
     * Regular expression to match the host.
     * <host> := '*' | *.<valid host characters> | <valid host characters>
     */
    private static final String HOST_REGEX = "((\\*)|(\\*\\.[^/\\*]+)|([^/\\*]+))";

    /**
     * Regular expression to match the path.
     * <path> := <any chars, `*` will match 0 or more characters>
     */
    private static final String PATH_REGEX = "(.*)";

    /**
     * Regular expression to match the pattern.
     * <pattern> := '*' | <scheme>://<host>/<path> | <scheme>://<host> | <scheme>:///<path> | <scheme>:/<path> | <scheme>:/
     */
    private static final String PATTERN_REGEX = String.format(Locale.US, "^((\\*)|((%s://%s/%s)|(%s://%s)|(%s:/[^/]%s)|(%s:/)|(%s:///%s)))",
            SCHEME_REGEX, HOST_REGEX, PATH_REGEX, SCHEME_REGEX, HOST_REGEX, SCHEME_REGEX, PATH_REGEX, SCHEME_REGEX, SCHEME_REGEX, PATH_REGEX);

    /**
     * Regular expression characters. Used to escape any regular expression from the path and host.
     */
    private static final String REGEX_SPECIAL_CHARACTERS = "\\.[]{}()^$?+|*";

    /**
     * Compiled pattern to validate url pattern entries.
     */
    private static final Pattern VALID_PATTERN = Pattern.compile(PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Interface that defines a callback that can be used to reject whitelisting of a URL.
     */
    public interface OnWhitelistCallback {

        /**
         * Called when the url has passed the isWhitelisted() check.
         *
         * @param url The URL.
         * @param scope The scope.
         * @return <code>true</code> to accept the whitelisting, <code>false</code> to reject the whitelisting.
         */
        boolean acceptWhitelisting(@NonNull String url, @Scope int scope);

    }

    @Nullable
    private OnWhitelistCallback whitelistCallback;

    private final List<Entry> entries = new ArrayList<>();
    private boolean isOpenUrlWhitelistingEnabled = true;

    /**
     * Adds an entry to the whitelist for URL matching. Patterns must be defined with the following
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
     * @param pattern The URL pattern to add as a whitelist matcher.
     * @return <code>true</code> if the pattern was added successfully, <code>false</code> if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    public boolean addEntry(@NonNull String pattern) {
        return addEntry(pattern, SCOPE_ALL);
    }

    /**
     * Adds an entry to the whitelist for URL matching. Patterns must be defined with the following
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
     * @param pattern The URL pattern to add as a whitelist matcher.
     * @param scope The scope that entry applies to.
     * @return <code>true</code> if the pattern was added successfully, <code>false</code> if the pattern
     * was unable to be added because it was either null or did not match the url-pattern syntax.
     */
    public boolean addEntry(@NonNull String pattern, @Scope int scope) {
        //noinspection ConstantConditions
        if (pattern == null || !VALID_PATTERN.matcher(pattern).matches()) {
            Logger.error("Invalid whitelist pattern %s", pattern);
            return false;
        }

        Uri uri = Uri.parse(pattern);
        String scheme = uri.getScheme();
        String host = uri.getEncodedAuthority();
        String path = uri.getPath();

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
     * Checks if a given URL is whitelisted or not with scope {@link #SCOPE_ALL}.
     *
     * @param url The URL.
     * @return <code>true</code> If the URL matches any entries in the whitelist.
     */
    public boolean isWhitelisted(@Nullable String url) {
        return isWhitelisted(url, SCOPE_ALL);
    }

    /**
     * Checks if a given URL is whitelisted or not.
     *
     * @param url The URL.
     * @param scope The scope.
     * @return <code>true</code> If the URL matches any entries in the whitelist.
     */
    public boolean isWhitelisted(@Nullable String url, @Scope int scope) {
        if (url == null) {
            return false;
        }

        boolean match;

        if (scope == SCOPE_OPEN_URL && !isOpenUrlWhitelistingEnabled) {
            match = true;
        } else {
            Uri uri = Uri.parse(url);
            int matchedScope = 0;

            synchronized (entries) {
                for (Entry entry : entries) {
                    if (entry.pattern.matches(uri)) {
                        matchedScope |= entry.scope;
                    }
                }
            }

            match = ((matchedScope & scope) == scope);
        }

        // if the url is whitelisted, allow the app to reject the whitelisting
        if (match && (whitelistCallback != null)) {
            match = whitelistCallback.acceptWhitelisting(url, scope);
        }

        return match;
    }

    /**
     * Enables/disbales {@link #SCOPE_OPEN_URL} checking. If disabled, all URL checks for the open
     * url scope will be allowed.
     *
     * @param isOpenUrlWhitelistingEnabled {@code true} to enable whitelist checking for {@link #SCOPE_OPEN_URL},
     * otherwise {@code false}.
     */
    public void setOpenUrlWhitelistingEnabled(boolean isOpenUrlWhitelistingEnabled) {
        this.isOpenUrlWhitelistingEnabled = isOpenUrlWhitelistingEnabled;
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
     * Factory method to create the default whitelist with values from the airship config.
     *
     * @param airshipConfigOptions The airship config options.
     * @return The default whitelist.
     * @hide
     */
    @NonNull
    public static Whitelist createDefaultWhitelist(@NonNull AirshipConfigOptions airshipConfigOptions) {
        Whitelist whitelist = new Whitelist();
        whitelist.addEntry("https://*.urbanairship.com");
        whitelist.addEntry("https://*.youtube.com", SCOPE_OPEN_URL);
        if (airshipConfigOptions.whitelist != null) {
            for (String entry : airshipConfigOptions.whitelist) {
                whitelist.addEntry(entry);
            }
        }

        whitelist.setOpenUrlWhitelistingEnabled(airshipConfigOptions.enableUrlWhitelisting);

        return whitelist;
    }

    /**
     * Sets the whitelist callback.
     *
     * @param whitelistCallback The whitelist callback.
     */
    public void setWhitelistCallback(@Nullable OnWhitelistCallback whitelistCallback) {
        this.whitelistCallback = whitelistCallback;
    }

    /**
     * Helper class that does the actual matching using the scheme and host patterns.
     */
    private class UriPattern {

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

            return path == null || (uri.getPath() != null && path.matcher(uri.getPath()).matches());
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
