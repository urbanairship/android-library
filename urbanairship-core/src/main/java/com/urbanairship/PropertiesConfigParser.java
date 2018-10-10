/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.util.UAStringUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Properties file config parser.
 */
class PropertiesConfigParser implements ConfigParser {

    private final List<String> propertyNames;
    private final List<String> propertyValues;
    private final Context context;

    private PropertiesConfigParser(@NonNull Context context, @NonNull List<String> propertyNames, @NonNull List<String> propertyValues) {
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
     * @throws IOException
     */
    @NonNull
    public static PropertiesConfigParser fromAssets(@NonNull Context context, @NonNull String propertiesFile) throws IOException {
        Resources resources = context.getResources();
        AssetManager assetManager = resources.getAssets();

        //bail if the properties file can't be found
        if (!Arrays.asList(assetManager.list("")).contains(propertiesFile)) {
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
                    Logger.error("PropertiesConfigParser - Failed to close input stream.", e);
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
        List<String> propertyValues = new ArrayList<>();

        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name);
            if (value != null) {
                value = value.trim();
            }

            if (UAStringUtil.isEmpty(value)) {
                continue;
            }

            propertyNames.add(name);
            propertyValues.add(value);
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
    public String getString(int index) {
        return propertyValues.get(index);
    }

    @NonNull
    @Override
    public String getString(int index, @NonNull String defaultValue) {
        String value = getString(index);
        return value == null ? defaultValue : value;
    }

    @Override
    public boolean getBoolean(int index) {
        return Boolean.parseBoolean(propertyValues.get(index));
    }

    @Nullable
    @Override
    public String[] getStringArray(int index) {
        return propertyValues.get(index).split("[, ]+");
    }

    @Override
    public int getDrawableResourceId(int index) {
        return context.getResources().getIdentifier(getString(index), "drawable", context.getPackageName());
    }

    @Override
    public int getColor(int index) {
        return Color.parseColor(propertyValues.get(index));
    }

    @Override
    public long getLong(int index) {
        return Long.parseLong(propertyValues.get(index));
    }
}
