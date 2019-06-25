/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.res.XmlResourceParser;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import android.util.AttributeSet;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.Closeable;
import java.io.IOException;

/**
 * XML Config parser.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class XmlConfigParser extends AttributeSetConfigParser implements Closeable {

    private final XmlResourceParser parser;

    private XmlConfigParser(@NonNull Context context, @NonNull AttributeSet attributeSet, @NonNull XmlResourceParser parser) {
        super(context, attributeSet);
        this.parser = parser;
    }

    /**
     * Parses an element from a Xml config file.
     *
     * @param context The context.
     * @param resId The Xml resource Id.
     * @param tag The element's tag.
     * @return An XmlConfigParser for the element.
     * @throws IOException
     * @throws XmlPullParserException
     */
    @NonNull
    public static XmlConfigParser parseElement(@NonNull Context context, int resId, @NonNull String tag) throws IOException, XmlPullParserException {
        XmlResourceParser parser = context.getResources().getXml(resId);
        AttributeSet attributeSet = null;

        int state;
        do {
            try {
                state = parser.next();
            } catch (XmlPullParserException | IOException e) {
                parser.close();
                throw e;
            }

            if (state == XmlPullParser.START_TAG && parser.getName().equals(tag)) {
                attributeSet = Xml.asAttributeSet(parser);
                break;
            }
        } while (state != XmlPullParser.END_DOCUMENT);

        if (attributeSet == null) {
            parser.close();
            throw new IOException("Element " + tag + " not found");
        }

        return new XmlConfigParser(context, attributeSet, parser);
    }

    /**
     * Closes the parser.
     */
    public void close() {
        if (parser != null) {
            parser.close();
        }
    }

}
