/* Copyright 2017 Urban Airship and Contributors */

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
        int resourceValue = attributeSet.getAttributeResourceValue(index, 0);
        if (resourceValue != 0) {
            return resourceValue;
        }

        String resourceName = attributeSet.getAttributeValue(index);
        if (resourceName != null) {
            return context.getResources().getIdentifier(getString(index), "drawable", context.getPackageName());
        }

        return 0;
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
