/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushMessage
import java.net.MalformedURLException
import java.net.URL

/**
 * Notification builder extender to add the public notification defined by a [PushMessage].
 */
public class StyleNotificationExtender public constructor(
    context: Context,
    private val message: PushMessage
) : NotificationCompat.Extender {

    private val context = context.applicationContext
    private var defaultStyle: NotificationCompat.Style? = null

    /**
     * Sets the default style if [PushMessage] does not define a style, or it fails to
     * create the style.
     *
     * @param defaultStyle The default style.
     * @return The StyleNotificationExtender to chain calls.
     */
    public fun setDefaultStyle(defaultStyle: NotificationCompat.Style?): StyleNotificationExtender {
        return this.also { it.defaultStyle = defaultStyle }
    }

    override fun extend(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        if (!applyStyle(builder)) {
            defaultStyle?.let { builder.setStyle(it) }
        }

        return builder
    }

    /**
     * Applies the notification style.
     *
     * @param builder The notification builder.
     * @return `true` if the style was applied, otherwise `false`.
     */
    private fun applyStyle(builder: NotificationCompat.Builder): Boolean {
        val stylePayload = message.stylePayload ?: return false

        val styleJson: JsonMap
        try {
            styleJson = JsonValue.parseString(stylePayload).optMap()
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse notification style payload.")
            return false
        }

        when (val type = styleJson.opt(TYPE_KEY).optString()) {
            BIG_TEXT_KEY -> {
                applyBigTextStyle(builder, styleJson)
                return true
            }

            INBOX_KEY -> {
                applyInboxStyle(builder, styleJson)
                return true
            }

            BIG_PICTURE_KEY -> return applyBigPictureStyle(builder, styleJson)

            else -> {
                UALog.e("Unrecognized notification style type: %s", type)
                return false
            }
        }
    }

    /**
     * Applies the big text notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     * @return `true` if the style was applied, otherwise `false`.
     */
    private fun applyBigTextStyle(
        builder: NotificationCompat.Builder,
        styleJson: JsonMap
    ): Boolean {
        val style = NotificationCompat.BigTextStyle()

        styleJson.opt(TITLE_KEY).string?.let { style.setBigContentTitle(it) }
        styleJson.opt(SUMMARY_KEY).string?.let { style.setSummaryText(it) }
        styleJson.opt(BIG_TEXT_KEY).string?.let { style.bigText(it) }

        builder.setStyle(style)
        return true
    }

    /**
     * Applies the big picture notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     * @return `true` if the style was applied, otherwise `false`.
     */
    private fun applyBigPictureStyle(
        builder: NotificationCompat.Builder,
        styleJson: JsonMap
    ): Boolean {
        val style = NotificationCompat.BigPictureStyle()

        val url = try {
            URL(styleJson.opt(BIG_PICTURE_KEY).optString())
        } catch (e: MalformedURLException) {
            UALog.e(e, "Malformed big picture URL.")
            return false
        }

        val bitmap = NotificationUtils.fetchBigImage(context, url) ?: return false

        // Set big picture image
        style.bigPicture(bitmap)

        // Clear the large icon when the big picture is expanded
        style.bigLargeIcon(null as Bitmap?)

        // Set the image as the large icon to show the image when collapsed
        builder.setLargeIcon(bitmap)

        styleJson.opt(TITLE_KEY).string?.let { style.setBigContentTitle(it) }
        styleJson.opt(SUMMARY_KEY).string?.let { style.setSummaryText(it) }

        builder.setStyle(style)
        return true
    }

    /**
     * Applies the inbox notification style.
     *
     * @param builder The notification builder.
     * @param styleJson The JsonMap style.
     */
    private fun applyInboxStyle(builder: NotificationCompat.Builder, styleJson: JsonMap) {
        val style = NotificationCompat.InboxStyle()

        styleJson.opt(LINES_KEY)
            .optList()
            .mapNotNull { it.string }
            .filter { it.isNotEmpty() }
            .forEach { style.addLine(it) }

        styleJson.opt(TITLE_KEY).string?.let { style.setBigContentTitle(it) }
        styleJson.opt(SUMMARY_KEY).string?.let { style.setSummaryText(it) }

        builder.setStyle(style)
    }

    public companion object {

        // Notification styles
        public const val TITLE_KEY: String = "title"
        public const val SUMMARY_KEY: String = "summary"
        public const val TYPE_KEY: String = "type"
        public const val BIG_TEXT_KEY: String = "big_text"
        public const val BIG_PICTURE_KEY: String = "big_picture"
        public const val INBOX_KEY: String = "inbox"
        public const val LINES_KEY: String = "lines"
    }
}
