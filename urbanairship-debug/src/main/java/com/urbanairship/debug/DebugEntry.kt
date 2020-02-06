package com.urbanairship.debug

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.RestrictTo
import androidx.annotation.XmlRes
import com.urbanairship.Logger
import com.urbanairship.util.UAStringUtil
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Debug entry.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class DebugEntry(
    val navigationId: Int,
    val title: String,
    val description: String?
) {

    companion object {
        private const val ENTRY_TAG = "entry"
        private const val NAVIGATION_ID_ATTRIBUTE = "navigationId"
        private const val TITLE_ATTRIBUTE = "title"
        private const val DESCRIPTION_ATTRIBUTE = "description"

        /**
         * Parses plugin info from a resource file.
         * @param context The context.
         * @param resource The resource file ID.
         * @return A collection of plugin infos.
         */
        fun parse(context: Context, @XmlRes resource: Int): List<DebugEntry> {

            var parser: XmlResourceParser? = null
            return try {
                parser = context.resources.getXml(resource)
                parse(context, parser)
            } catch (e: Exception) {
                Logger.error(e, "Failed to parse ua_debug_entries config.")
                emptyList()
            } finally {
                parser?.close()
            }
        }

        @Throws(IOException::class, XmlPullParserException::class)
        private fun parse(context: Context, parser: XmlResourceParser): List<DebugEntry> {
            val screens = ArrayList<DebugEntry>()

            while (parser.next() != XmlPullParser.END_DOCUMENT) {

                // Start component
                if (parser.eventType == XmlPullParser.START_TAG && ENTRY_TAG == parser.name) {
                    val navigationId = parser.getAttributeResourceValue(null, NAVIGATION_ID_ATTRIBUTE, 0)
                    val title = getString(context, parser, TITLE_ATTRIBUTE)
                    val description = getString(context, parser, DESCRIPTION_ATTRIBUTE)

                    if (navigationId == 0) {
                        Logger.error("%s missing navigation id.", NAVIGATION_ID_ATTRIBUTE)
                        continue
                    }

                    if (UAStringUtil.isEmpty(title)) {
                        Logger.error("%s missing title.", TITLE_ATTRIBUTE)
                        continue
                    }

                    screens.add(DebugEntry(navigationId, title!!, description))
                }
            }

            return screens
        }

        private fun getString(context: Context, parser: XmlResourceParser, identifier: String): String? {
            val resourceId = parser.getAttributeResourceValue(null, identifier, 0)
            if (resourceId != 0) {
                return context.resources.getString(resourceId)
            }

            return parser.getAttributeValue(null, identifier)
        }
    }
}
