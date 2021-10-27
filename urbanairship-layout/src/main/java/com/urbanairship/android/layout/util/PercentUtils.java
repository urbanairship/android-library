/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public final class PercentUtils {
    private static final Pattern PATTERN_NON_NUMERIC = Pattern.compile("[^\\d.]");
    private static final Pattern PATTERN_PERCENT_W_SYMBOL = Pattern.compile("^\\d{1,3}%$");

    private PercentUtils() {}

    public static boolean isPercent(@NonNull String string) {
        return PATTERN_PERCENT_W_SYMBOL.matcher(string).matches();
    }

    public static float parse(@NonNull String percentString) {
        String number = digits(percentString);
        return Float.parseFloat(number) / 100f;
    }

    @NonNull
    public static String digits(@NonNull String number) {
        return PATTERN_NON_NUMERIC.matcher(number).replaceAll("");
    }
}
