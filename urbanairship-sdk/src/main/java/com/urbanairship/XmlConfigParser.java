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
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * XML Config parser.
 */
class XmlConfigParser implements ConfigParser {

    private static final String CONFIG_ELEMENT = "AirshipConfigOptions";

    private final Context context;

    private XmlResourceParser parser;
    private AttributeSet attributeSet;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param resId The config resource ID.
     * @throws IOException
     * @throws XmlPullParserException
     */
    XmlConfigParser(Context context, int resId) throws IOException, XmlPullParserException {
        this.context = context;
        parser = context.getResources().getXml(resId);

        int state;
        do {
            try {
                state = parser.next();
            } catch (XmlPullParserException | IOException e) {
                parser.close();
                throw e;
            }

            if (state == XmlPullParser.START_TAG && parser.getName().equals(CONFIG_ELEMENT)) {
                attributeSet = Xml.asAttributeSet(parser);
                break;
            }
        } while (state != XmlPullParser.END_DOCUMENT);

        if (attributeSet == null) {
            parser.close();
            throw new IllegalArgumentException("Config missing AirshipConfigOptions element.");
        }
    }

    @Override
    public int getCount() {
        return attributeSet.getAttributeCount();
    }

    @Override
    public String getName(int index) {
        return attributeSet.getAttributeName(index);
    }

    @Override
    public String getString(int index) {
        int resourceId = attributeSet.getAttributeResourceValue(index, 0);
        if (resourceId != 0) {
            return context.getString(resourceId);
        }

        return attributeSet.getAttributeValue(index);
    }

    @Override
    public boolean getBoolean(int index) {
        int resourceId = attributeSet.getAttributeResourceValue(index, 0);
        if (resourceId != 0) {
            return context.getResources().getBoolean(resourceId);
        }

        return attributeSet.getAttributeBooleanValue(index, false);
    }

    @Override
    public String[] getStringArray(int index) {
        int resourceId = attributeSet.getAttributeResourceValue(index, 0);
        if (resourceId != 0) {
            return context.getResources().getStringArray(resourceId);
        }
        return null;
    }

    @Override
    public int getDrawableResourceId(int index) {
        return attributeSet.getAttributeResourceValue(index, 0);
    }

    @Override
    public int getColor(int index) {
        int resourceId = attributeSet.getAttributeResourceValue(index, 0);
        if (resourceId != 0) {
            return ContextCompat.getColor(context, resourceId);
        }

        return Color.parseColor(attributeSet.getAttributeValue(index));
    }

    @Override
    public long getLong(int index) {
        return Long.parseLong(getString(index));
    }

    /**
     * Closes the parser.
     */
    public void close() {
        parser.close();
        attributeSet = null;
    }
}
