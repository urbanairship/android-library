package com.urbanairship.debug

import android.content.Context
import android.content.res.XmlResourceParser
import android.support.annotation.RestrictTo
import android.support.annotation.XmlRes
import com.urbanairship.Logger
import com.urbanairship.util.UAStringUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Debug screen entry.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class DebugScreenEntry(val activityClass: String,
                            val title: String,
                            val description: String?) {

    companion object {
        private const val ENTRY_TAG = "entry"
        private const val ACTIVITY_ATTRIBUTE = "activity"
        private const val TITLE_ATTRIBUTE = "title"
        private const val DESCRIPTION_ATTRIBUTE = "description"

        /**
         * Parses plugin info from a resource file.
         * @param context The context.
         * @param resource The resource file ID.
         * @return A collection of plugin infos.
         */
        fun parse(context: Context, @XmlRes resource: Int): List<DebugScreenEntry> {
            var parser: XmlResourceParser? = null
            return try {
                parser = context.resources.getXml(resource)
                parse(context, parser!!)
            } catch (e: Exception) {
                Logger.error(e, "Failed to parse screens config.")
                emptyList()
            } finally {
                parser?.close()
            }
        }

        @Throws(IOException::class, XmlPullParserException::class)
        private fun parse(context: Context, parser: XmlResourceParser): List<DebugScreenEntry> {
            val screens = ArrayList<DebugScreenEntry>()

            while (parser.next() != XmlPullParser.END_DOCUMENT) {

                // Start component
                if (parser.eventType == XmlPullParser.START_TAG && ENTRY_TAG == parser.name) {
                    val activity = getString(context, parser, ACTIVITY_ATTRIBUTE)
                    val title = getString(context, parser, TITLE_ATTRIBUTE)
                    val description = getString(context, parser, DESCRIPTION_ATTRIBUTE)

                    if (UAStringUtil.isEmpty(activity)) {
                        Logger.error("%s missing activity.", ACTIVITY_ATTRIBUTE)
                        continue
                    }

                    if (UAStringUtil.isEmpty(title)) {
                        Logger.error("%s missing title.", TITLE_ATTRIBUTE)
                        continue
                    }

                    screens.add(DebugScreenEntry(activity!!, title!!, description))
                }
            }

            return screens;
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
