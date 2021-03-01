/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;

import com.urbanairship.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Properties file config parser.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PropertiesConfigParser implements ConfigParser {

    private final List<String> propertyNames;
    private final Map<String, String> propertyValues;

    private final Context context;

    private PropertiesConfigParser(@NonNull Context context, @NonNull List<String> propertyNames, @NonNull Map<String, String> propertyValues) {
        this.context = context;
        this.propertyNames = propertyNames;
        this.propertyValues = propertyValues;
    }

    /**
     * Factory method to create a config parser from a file in the assets directory.
     *
     * @param context The application context.
     * @param propertiesFile The properties file.
     * @return A PropertiesConfigParser instance.
     * @throws IOException if properties file cannot be found.
     */
    @NonNull
    public static PropertiesConfigParser fromAssets(@NonNull Context context, @NonNull String propertiesFile) throws IOException {
        Resources resources = context.getResources();
        AssetManager assetManager = resources.getAssets();

        String[] assets = assetManager.list("");
        //bail if the properties file can't be found
        if (assets == null || !Arrays.asList(assets).contains(propertiesFile)) {
            throw new FileNotFoundException("Unable to find properties file: " + propertiesFile);
        }

        Properties properties = new Properties();
        InputStream inStream = null;

        try {
            inStream = assetManager.open(propertiesFile);
            properties.load(inStream);
            return fromProperties(context, properties);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    Logger.debug(e, "Failed to close input stream.");
                }
            }
        }
    }

    /**
     * Factory method to create a config parser.
     *
     * @param context The application context.
     * @param properties The properties.
     * @return A PropertiesConfigParser instance.
     */
    @NonNull
    public static PropertiesConfigParser fromProperties(@NonNull Context context, @NonNull Properties properties) {
        List<String> propertyNames = new ArrayList<>();
        Map<String, String> propertyValues = new HashMap<>();

        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name);
            if (value != null) {
                value = value.trim();
            }

            if (UAStringUtil.isEmpty(value)) {
                continue;
            }

            propertyNames.add(name);
            propertyValues.put(name, value);
        }

        return new PropertiesConfigParser(context, propertyNames, propertyValues);
    }

    @Override
    public int getCount() {
        return propertyNames.size();
    }

    @Nullable
    @Override
    public String getName(int index) {
        return propertyNames.get(index);
    }

    @Nullable
    @Override
    public String getString(@NonNull String name) {
        return propertyValues.get(name);
    }

    @NonNull
    @Override
    public String getString(@NonNull String name, @NonNull String defaultValue) {
        String value = getString(name);
        return value == null ? defaultValue : value;
    }

    @Override
    public boolean getBoolean(@NonNull String name, boolean defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value);
    }

    @Nullable
    @Override
    public String[] getStringArray(@NonNull String name) {
        String value = propertyValues.get(name);
        if (value == null) {
            return new String[0];
        }

        return value.split("[, ]+");
    }

    @Override
    public int getDrawableResourceId(@NonNull String name) {
        return context.getResources().getIdentifier(getString(name), "drawable", context.getPackageName());
    }

    @Override
    public int getRawResourceId(@NonNull String name) {
        return context.getResources().getIdentifier(getString(name), "raw", context.getPackageName());
    }

    @Override
    public long getLong(@NonNull String name, long defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Long.parseLong(value);
    }

    @Override
    public int getInt(@NonNull String name, int defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    @Override
    @ColorInt
    public int getColor(@NonNull String name, @ColorInt int defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Color.parseColor(value);
    }

}
