/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Xml
import androidx.annotation.XmlRes
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.push.notifications.NotificationActionButton
import com.urbanairship.push.notifications.NotificationActionButtonGroup
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Utility class to parse [NotificationActionButtonGroup].
 */
internal object ActionButtonGroupsParser {

    private const val BUTTON_GROUP_TAG = "UrbanAirshipActionButtonGroup"
    private const val BUTTON_TAG = "UrbanAirshipActionButton"

    private const val ID_ATTRIBUTE = "id"
    private const val DESCRIPTION_ATTRIBUTE = "description"
    private const val FOREGROUND_ATTRIBUTE = "foreground"

    /**
     * Generates a map of NotificationActionButtonGroups from an xml resource file.
     *
     * @param context The application context.
     * @param resource The xml resource.
     * @return A map of NotificationActionButtonGroups.
     */
    fun fromXml(
        context: Context, @XmlRes resource: Int
    ): Map<String, NotificationActionButtonGroup> {
        try {
            val parser = context.resources.getXml(resource)
            return parseGroups(context, parser)
        } catch (e: IOException) {
            // Note: NullPointerException can occur in rare circumstances further down the call stack
            UALog.e(e, "Failed to parse NotificationActionButtonGroups.")
            return emptyMap()
        } catch (e: XmlPullParserException) {
            UALog.e(e, "Failed to parse NotificationActionButtonGroups.")
            return emptyMap()
        } catch (e: Resources.NotFoundException) {
            UALog.e(e, "Failed to parse NotificationActionButtonGroups.")
            return emptyMap()
        } catch (e: NullPointerException) {
            UALog.e(e, "Failed to parse NotificationActionButtonGroups.")
            return emptyMap()
        }
    }

    /**
     * Parses [NotificationActionButtonGroup] from xml.
     *
     * @param context The context.
     * @param parser The xml parser.
     * @return A map of [NotificationActionButtonGroup].
     * @throws IOException if the AirshipConfigOptions element is missing.
     * @throws XmlPullParserException if XML parsing fails to get next element.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun parseGroups(
        context: Context, parser: XmlResourceParser
    ): Map<String, NotificationActionButtonGroup> {
        val groups = mutableMapOf<String, NotificationActionButtonGroup>()

        var groupId: String? = null
        var groupBuilder: NotificationActionButtonGroup.Builder? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val tagType = parser.eventType
            val tagName = parser.name

            // Start group
            if (tagType == XmlPullParser.START_TAG && BUTTON_GROUP_TAG == tagName) {
                val id = parser.getAttributeValue(null, ID_ATTRIBUTE)
                if (id.isEmpty()) {
                    UALog.e("$BUTTON_GROUP_TAG missing id.")
                    continue
                }

                groupId = id
                groupBuilder = NotificationActionButtonGroup.newBuilder()

                continue
            }

            if (groupId.isNullOrEmpty()) {
                continue
            }

            // Inner Buttons
            if (tagType == XmlPullParser.START_TAG && BUTTON_TAG == tagName) {
                val buttonId = parser.getAttributeValue(null, ID_ATTRIBUTE)
                if (buttonId.isEmpty()) {
                    UALog.e("%s missing id.", BUTTON_TAG)
                    continue
                }

                val attributeSet = Xml.asAttributeSet(parser)
                val typedArray = context.obtainStyledAttributes(
                    attributeSet, R.styleable.UrbanAirshipActionButton
                )

                val builder = NotificationActionButton.newBuilder(buttonId)
                    .setPerformsInForeground(
                        parser.getAttributeBooleanValue(null, FOREGROUND_ATTRIBUTE, true))
                    .setIcon(
                        typedArray.getResourceId(R.styleable.UrbanAirshipActionButton_android_icon, 0))
                    .setDescription(parser.getAttributeValue(null, DESCRIPTION_ATTRIBUTE))

                val labelId = typedArray.getResourceId(R.styleable.UrbanAirshipActionButton_android_label, 0)
                if (labelId != 0) {
                    builder.setLabel(labelId)
                } else {
                    builder.setLabel(typedArray.getString(R.styleable.UrbanAirshipActionButton_android_label))
                }

                groupBuilder?.addNotificationActionButton(builder.build())

                typedArray.recycle()

                continue
            }

            // End Group
            if (tagType == XmlPullParser.END_TAG && BUTTON_GROUP_TAG == tagName) {
                val group = groupBuilder?.build() ?: continue
                if (group.notificationActionButtons.isEmpty()) {
                    UALog.e("%s %s missing action buttons.", BUTTON_GROUP_TAG, groupId)
                    continue
                }

                groups[groupId] = group
            }
        }
        return groups
    }
}
