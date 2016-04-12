/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

package com.urbanairship;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;

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

    private final List<String> propertyNames = new ArrayList<>();
    private final List<String> propertyValues = new ArrayList<>();
    private final Context context;

    public PropertiesConfigParser(Context context, String propertiesFile) throws IOException {
        this.context = context;
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

    @Override
    public int getCount() {
        return propertyNames.size();
    }

    @Override
    public String getName(int index) {
        return propertyNames.get(index);
    }

    @Override
    public String getString(int index) {
        return propertyValues.get(index);
    }

    @Override
    public boolean getBoolean(int index) {
        return Boolean.parseBoolean(propertyValues.get(index));
    }

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
