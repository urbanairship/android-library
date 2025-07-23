/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import androidx.annotation.XmlRes
import com.urbanairship.UALog
import com.urbanairship.util.UAStringUtil
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Utility class to parse a list of [com.urbanairship.actions.ActionRegistry.Entry] from XML.
 *
 * The action entry format expected by this parser is given below. Both the class attribute and
 * at least one name are required. Predicate attributes are optional.
 *
 * <ActionEntry class="com.package.className" predicate="com.package.predicateClassName"></ActionEntry>
 * <name>name</name>
 * <name>alternateName</name>
 *
 */
internal object ActionEntryParser {

    private const val ACTION_ENTRY_TAG = "ActionEntry"
    private const val NAME_TAG = "name"

    private const val CLASS_ATTRIBUTE = "class"
    private const val PREDICATE_ATTRIBUTE = "predicate"

    /**
     * Generates a list of action entries from an xml resource file.
     *
     * @param context The application context.
     * @param resource The xml resource.
     * @return A list of action entries.
     */
    fun fromXml(context: Context, @XmlRes resource: Int): List<ActionRegistry.Entry> {
        val parser = context.resources.getXml(resource)

        try {
            return parseEntries(parser)
        } catch(ex: Exception) {
            when(ex) {
                is IOException,
                is XmlPullParserException,
                is Resources.NotFoundException,
                is NullPointerException -> {
                    UALog.e(ex, "Failed to parse action entries.")
                    return ArrayList()
                }
                else -> throw ex
            }
        } finally {
            parser.close()
        }
    }

    /**
     * Parses a list of action entries from an xml resource file.
     *
     * @param parser The xml parser.
     * @return A list of action entries.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun parseEntries(parser: XmlResourceParser): List<ActionRegistry.Entry> {
        val entries = ArrayList<ActionRegistry.Entry>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val tagType = parser.eventType
            val tagName = parser.name

            // Start ActionEntry
            if (tagType == XmlPullParser.START_TAG && ACTION_ENTRY_TAG == tagName) {
                parseEntry(parser)
                    ?.let(entries::add)
            }
        }

        return entries
    }

    /**
     * Parses an action entry from an xml resource file.
     *
     * @param parser The xml parser.
     * @return The action entry, or null if one could not be found.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun parseEntry(parser: XmlResourceParser): ActionRegistry.Entry? {
        val className = parser.getAttributeValue(null, CLASS_ATTRIBUTE)
        val predicateClassName = parser.getAttributeValue(null, PREDICATE_ATTRIBUTE)

        val names = ArrayList<String>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val tagType = parser.eventType
            val tagName = parser.name

            // Start name
            if (tagType == XmlPullParser.START_TAG && NAME_TAG == tagName) {
                parseName(parser)
                    ?.let(names::add)
            }

            // End ActionEntry
            if (tagType == XmlPullParser.END_TAG && ACTION_ENTRY_TAG == tagName) {
                break
            }
        }

        if (names.isEmpty()) {
            UALog.e("Action names not found.")
            return null
        }

        val clazz: Class<out Action>
        try {
            clazz = Class.forName(className).asSubclass(Action::class.java)
        } catch (e: ClassNotFoundException) {
            UALog.e("Action class $className not found.")
            return null
        }

        val entry = ActionRegistry.Entry(defaultActionClass = clazz, names = names)

        if (!UAStringUtil.isEmpty(predicateClassName)) {
            try {
                val predicate = Class
                    .forName(predicateClassName)
                    .asSubclass(ActionRegistry.Predicate::class.java)
                    .newInstance()
                entry.predicate = predicate
            } catch (e: Exception) {
                UALog.e("Predicate class $predicateClassName not found. Skipping predicate.")
            }
        }

        return entry
    }

    /**
     * Parses an action name from an xml resource file.
     *
     * @param parser The xml parser.
     * @return The action name, or null if one could not be found.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun parseName(parser: XmlResourceParser): String? {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val tagType = parser.eventType
            val tagName = parser.name

            // Name text
            if (tagType == XmlPullParser.TEXT) {
                return parser.text
            }

            // End Name
            if (tagType == XmlPullParser.END_TAG && NAME_TAG == tagName) {
                break
            }
        }

        return null
    }
}
