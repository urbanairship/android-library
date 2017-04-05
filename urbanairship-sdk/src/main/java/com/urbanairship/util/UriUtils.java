/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;


import android.net.Uri;
import android.support.annotation.NonNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class containing utility methods related to URI data.
 */
public class UriUtils {

    /**
     * Gets all of the query parameters and values as a map from a
     * given uri.
     *
     * @param uri The uri to parse
     * @return A map of query parameter name to values
     */
    public static Map<String, List<String>> getQueryParameters(@NonNull Uri uri) {
        Map<String, List<String>> parameters = new HashMap<>();

        String query = uri.getEncodedQuery();

        if (UAStringUtil.isEmpty(query)) {
            return parameters;
        }

        for (String param : query.split("&")) {
            String[] keyValuePair = param.split("=");

            String name = keyValuePair.length >= 1 ? Uri.decode(keyValuePair[0]) : null;
            String value = keyValuePair.length >= 2 ? Uri.decode(keyValuePair[1]) : null;

            if (!UAStringUtil.isEmpty(name)) {

                if (!parameters.containsKey(name)) {
                    parameters.put(name, new ArrayList<String>());
                }

                parameters.get(name).add(value);
            }
        }

        return parameters;
    }

    /**
     * Helper method that parses an object as a Uri
     *
     * @param value The value to parse
     * @return A Uri representation of the value, or <code>null</code> if the value
     * is not able to be parsed to a Uri.
     */
    public static Uri parse(Object value) {
        if (value == null || !(value instanceof String || value instanceof Uri || value instanceof URL)) {
            return null;
        }

        return Uri.parse(String.valueOf(value));
    }
}
