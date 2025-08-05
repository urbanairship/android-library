/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Xml
import androidx.annotation.RestrictTo
import java.io.Closeable
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * XML Config parser.
 *
 * @hide
 */
internal class XmlConfigParser private constructor(
    context: Context, attributeSet:
    AttributeSet,
    private val parser: XmlResourceParser
) : AttributeSetConfigParser(context, attributeSet), Closeable {

    /**
     * Closes the parser.
     */
    override fun close() {
        parser?.close()
    }

    public companion object {

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
        @Throws(IOException::class, XmlPullParserException::class)
        public fun parseElement(context: Context, resId: Int, tag: String): XmlConfigParser {
            val parser = context.resources.getXml(resId)
            var attributeSet: AttributeSet? = null

            var state: Int
            do {
                try {
                    state = parser.next()
                } catch (e: XmlPullParserException) {
                    parser.close()
                    throw e
                } catch (e: IOException) {
                    parser.close()
                    throw e
                }

                if (state == XmlPullParser.START_TAG && parser.name == tag) {
                    attributeSet = Xml.asAttributeSet(parser)
                    break
                }
            } while (state != XmlPullParser.END_DOCUMENT)

            if (attributeSet == null) {
                parser.close()
                throw IOException("Element $tag not found")
            }

            return XmlConfigParser(context, attributeSet, parser)
        }
    }
}
