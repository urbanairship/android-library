/* Copyright Airship and Contributors */

package com.urbanairship.iam.legacy

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.util.ObjectsCompat
import com.urbanairship.iam.content.Banner
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.push.PushMessage
import com.urbanairship.util.DateUtils
import java.util.concurrent.TimeUnit

/**
 * Legacy in-app message model object.
 */
public class LegacyInAppMessage @JvmOverloads public constructor(

    /**
     * The message's ID.
     */
    public val id: String,

    /**
     * Banner placement.
     */
    public val placement: Banner.Placement,

    /**
     * The alert.
     */
    public val alert: String? = null,

    /**
     * Display duration in milliseconds.
     */
    public val displayDurationMs: Long? = null,

    /**
     * The expiry date in milliseconds.
     */
    public val expiryMs: Long? = null,

    /**
     * Click actions.
     */
    public val clickActionValues: JsonMap? = null,

    /**
     *  The button group can be fetched from [com.urbanairship.push.PushManager.getNotificationActionGroup]
     */
    public val buttonGroupId: String? = null,

    /**
     * Button actions.
     */
    public val buttonActionValues: Map<String, JsonMap>? = null,

    /**
     * Primary Color
     */
    @ColorInt
    public val primaryColor: Int? = null,

    /**
     * Secondary Color
     */
    @ColorInt
    public val secondaryColor: Int? = null,

    /**
     * Message type.
     */
    private val messageType: String? = null,

    /**
     * Push campaign.
     */
    private val campaigns: JsonValue? = null,

    /**
     * Message extras.
     */
    public val extras: JsonMap? = null
) {

    internal companion object {

        // JSON keys
        private const val BANNER_TYPE = "banner"
        private const val DISPLAY_KEY = "display"
        private const val ACTIONS_KEY = "actions"
        private const val TYPE_KEY = "type"
        private const val EXTRA_KEY = "extra"
        private const val ALERT_KEY = "alert"
        private const val PRIMARY_COLOR_KEY = "primary_color"
        private const val SECONDARY_COLOR_KEY = "secondary_color"
        private const val DURATION_KEY = "duration"
        private const val EXPIRY_KEY = "expiry"
        private const val POSITION_KEY = "position"
        private const val ON_CLICK_KEY = "on_click"
        private const val BUTTON_GROUP_KEY = "button_group"
        private const val BUTTON_ACTIONS_KEY = "button_actions"
        private const val MESSAGE_TYPE_KEY = "message_type"
        private const val CAMPAIGNS_KEY = "campaigns"
        private const val MESSAGE_CENTER_ACTION = "^mc"

        /**
         * Creates an in-app message from a push message.
         *
         * @param pushMessage The push message.
         * @return The in-app message or null if the push did not contain an in-app message.
         * @throws JsonException If the JSON payload is unable to parsed.
         */
        @Throws(JsonException::class)
        fun fromPush(pushMessage: PushMessage): LegacyInAppMessage? {
            val sendId = pushMessage.sendId
            val json = JsonValue.parseString(pushMessage.getExtra(PushMessage.EXTRA_IN_APP_MESSAGE)).map

            if (json == null || sendId == null) {
                return null
            }

            val displayJson = json.requireField<JsonMap>(DISPLAY_KEY)
            if (BANNER_TYPE != displayJson.optionalField<String>(TYPE_KEY)) {
                throw JsonException("Only banner types are supported.")
            }

            val actionsJson = json.optionalField<JsonMap>(ACTIONS_KEY)
            val clickActions = actionsJson?.optionalField<JsonMap>(ON_CLICK_KEY)?.map?.toMutableMap() ?: mutableMapOf()
            if (pushMessage.richPushMessageId != null) {
                clickActions[MESSAGE_CENTER_ACTION] = JsonValue.wrap(pushMessage.richPushMessageId)
            }

            val buttonActions = actionsJson?.optionalField<JsonMap>(BUTTON_ACTIONS_KEY)?.map?.mapValues {
                it.value.optMap()
            }

            return LegacyInAppMessage(
                id = sendId,
                placement = displayJson.get(POSITION_KEY)?.let { Banner.Placement.fromJson(it) } ?: Banner.Placement.TOP,
                alert = displayJson.optionalField(ALERT_KEY),
                displayDurationMs = displayJson.optionalField<Long>(DURATION_KEY)?.let {
                    TimeUnit.SECONDS.toMillis(it)
                },
                expiryMs = json.optionalField<String>(EXPIRY_KEY)?.let { DateUtils.parseIso8601(it) },
                clickActionValues = clickActions.let { if (it.isNotEmpty()) JsonMap(it) else null },
                buttonGroupId = actionsJson?.optionalField(BUTTON_GROUP_KEY),
                buttonActionValues = buttonActions,
                primaryColor = displayJson.optionalField<String>(PRIMARY_COLOR_KEY)?.let {
                    try { Color.parseColor(it) } catch (e: Exception) { throw JsonException("Invalid primary color $it", e) }
                },
                secondaryColor = displayJson.optionalField<String>(SECONDARY_COLOR_KEY)?.let {
                    try { Color.parseColor(it) } catch (e: Exception) { throw JsonException("Invalid secondary color $it", e) }
                },
                messageType = json.optionalField(MESSAGE_TYPE_KEY),
                campaigns = json.optionalField(CAMPAIGNS_KEY),
                extras = json.optionalField(EXTRA_KEY)
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LegacyInAppMessage

        if (id != other.id) return false
        if (placement != other.placement) return false
        if (alert != other.alert) return false
        if (displayDurationMs != other.displayDurationMs) return false
        if (expiryMs != other.expiryMs) return false
        if (clickActionValues != other.clickActionValues) return false
        if (buttonGroupId != other.buttonGroupId) return false
        if (buttonActionValues != other.buttonActionValues) return false
        if (primaryColor != other.primaryColor) return false
        if (secondaryColor != other.secondaryColor) return false
        if (messageType != other.messageType) return false
        if (campaigns != other.campaigns) return false
        if (extras != other.extras) return false

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            id, placement, alert, displayDurationMs, expiryMs, clickActionValues,
            buttonActionValues, buttonGroupId, primaryColor, secondaryColor,
            messageType, campaigns, expiryMs
        )
    }

    override fun toString(): String {
        return "LegacyInAppMessage(id='$id', placement=$placement, alert=$alert, " +
                "displayDurationMs=$displayDurationMs, expiryMs=$expiryMs, " +
                "clickActionValues=$clickActionValues, buttonGroupId=$buttonGroupId, " +
                "buttonActionValues=$buttonActionValues, primaryColor=$primaryColor, " +
                "secondaryColor=$secondaryColor, messageType=$messageType, campaigns=$campaigns, " +
                "extras=$extras)"
    }
}
