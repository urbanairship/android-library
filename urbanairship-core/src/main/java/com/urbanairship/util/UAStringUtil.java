/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.util.Base64;

import com.urbanairship.Logger;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class containing utility methods related to strings.
 */
public abstract class UAStringUtil {

    /**
     * Builds a string.
     *
     * @param repeater The string to build.
     * @param times The number of times to append the string.
     * @param separator The separator string.
     * @return The new string.
     */
    @NonNull
    public static String repeat(@NonNull String repeater, int times, @NonNull String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(repeater);
            if (i + 1 != times) {
                builder.append(separator);
            }
        }
        return builder.toString();
    }

    /**
     * Checks if the string is empty.
     *
     * @param stringToCheck The string to check.
     * @return <code>true</code> if the string is null, <code>false</code> otherwise.
     */
    public static boolean isEmpty(@Nullable String stringToCheck) {
        return stringToCheck == null || stringToCheck.length() == 0;
    }

    /**
     * Checks if the strings are equal.
     *
     * @param firstString The first string.
     * @param secondString The second string.
     * @return <code>true</code> if the strings are equal, <code>false</code> otherwise.
     */
    public static boolean equals(@Nullable String firstString, @Nullable String secondString) {
        return firstString == null ? secondString == null : firstString.equals(secondString);
    }

    /**
     * Append a collection of strings and delimiter.
     *
     * @param c A collection of strings.
     * @param delimiter A delimiter string.
     * @return The new string.
     */
    @NonNull
    public static String join(@NonNull Collection<String> c, @NonNull String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = c.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (iter.hasNext()) {
                builder.append(delimiter);
            }
        }
        return builder.toString();
    }

    /**
     * Returns the sha256 hex string for a given string.
     *
     * @param value The value.
     * @return The sha256 hex string or null if the value is null or it failed encode the string.
     */
    @Nullable
    public static String sha256(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes("UTF-8"));
            return byteToHex(hash);
        } catch (@NonNull NoSuchAlgorithmException | UnsupportedEncodingException e) {
            Logger.error(e, "Failed to encode string: %s", value);
            return null;
        }
    }

    /**
     * Generates the sha256 digest for a given string.
     *
     * @param value The string.
     * @return The sha256 digest for the string, or null if it failed
     * to create the digest.
     */
    @Nullable
    public static byte[] sha256Digest(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes("UTF-8"));
        } catch (@NonNull NoSuchAlgorithmException | UnsupportedEncodingException e) {
            Logger.error(e, "Failed to encode string: %s", value);
            return null;
        }
    }

    /**
     * Converts the bytes into a hex string.
     *
     * @param bytes The byte array.
     * @return A hex string.
     */
    @NonNull
    public static String byteToHex(@NonNull byte[] bytes) {
        StringBuilder hexHash = new StringBuilder();
        for (byte b : bytes) {
            hexHash.append(String.format("%02x", b));
        }
        return hexHash.toString();
    }

    /**
     * Base64 decodes a string.
     *
     * @param encoded The base64 encoded string.
     * @return The decoded bytes or null if it failed to be decoded.
     */
    @Nullable
    public static byte[] base64Decode(@Nullable String encoded) {
        if (UAStringUtil.isEmpty(encoded)) {
            return null;
        }

        // Decode it
        try {
            return Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            Logger.verbose("Failed to decode string: %s", encoded);
            return null;
        }
    }

    /**
     * Generates a base 64 decoded string.
     *
     * @param encoded The encoded value.
     * @return The decoded string or null if it failed to be decoded.
     */
    @Nullable
    public static String base64DecodedString(@Nullable String encoded) {
        byte[] decoded = base64Decode(encoded);
        if (decoded == null) {
            return null;
        }

        try {
            return new String(decoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Logger.error(e, "Failed to create string");
            return null;
        }
    }

    @Nullable
    public static String nullIfEmpty(@Nullable String value) {
        if (UAStringUtil.isEmpty(value)) {
            return null;
        }

        return value;
    }

    @NonNull
    public static String namedStringResource(@NonNull Context context, @NonNull String name, @NonNull String defaultValue) {
        int resourceId = context.getResources().getIdentifier(name, "string", context.getApplicationInfo().packageName);
        if (resourceId == 0) {
            return defaultValue;
        } else {
            return context.getString(resourceId);
        }
    }
}
