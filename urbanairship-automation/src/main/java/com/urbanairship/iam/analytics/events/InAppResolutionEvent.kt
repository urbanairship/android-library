/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.concurrent.TimeUnit

internal class InAppResolutionEvent(
    reportData: JsonSerializable?
) : InAppEvent {

    override val name: String = "in_app_resolution"
    override val data: JsonSerializable? = reportData

    companion object {
        fun buttonTap(identifier: String, description: String, displayTime: Long): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.ButtonTap(identifier, description),
                    displayTime = displayTime
                )
            )
        }

        fun messageTap(displayTime: Long): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.MessageTap,
                    displayTime = displayTime
                )
            )
        }

        fun userDismissed(displayTime: Long): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.UserDismissed,
                    displayTime = displayTime
                )
            )
        }

        fun timedOut(displayTime: Long): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.TimedOut,
                    displayTime = displayTime
                )
            )
        }

        fun interrupted(): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.Interrupted,
                    displayTime = 0L
                )
            )
        }

        fun control(experimentResult: ExperimentResult): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.Control,
                    displayTime = 0L,
                    deviceInfo = DeviceInfo(
                        channel = experimentResult.channelId,
                        contact = experimentResult.contactId
                    )
                )
            )
        }

        fun audienceExcluded(): InAppResolutionEvent {
            return InAppResolutionEvent(
                ResolutionData(
                    resolutionType = ResolutionData.ResolutionType.AudienceExcluded,
                    displayTime = 0L
                )
            )
        }
    }


    private data class DeviceInfo(
        val channel: String?,
        val contact: String?
    ) : JsonSerializable {
        companion object {
            private const val CHANNEL = "channel_id"
            private const val CONTACT = "contact_id"
        }
        override fun toJsonValue(): JsonValue = jsonMapOf(
            CHANNEL to channel,
            CONTACT to contact
        ).toJsonValue()
    }

    private data class ResolutionData(
        val resolutionType: ResolutionType,
        val displayTime: Long,
        val deviceInfo: DeviceInfo? = null
    ) : JsonSerializable {

        companion object {
            private const val RESOLUTION = "resolution"
            private const val DISPLAY_TIME = "display_time"
            private const val DEVICE = "device"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            RESOLUTION to JsonMap
                .newBuilder()
                .putAll(resolutionType.toJsonValue().optMap())
                .put(DISPLAY_TIME, TimeUnit.MILLISECONDS.toSeconds(displayTime))
                .build(),
            DEVICE to deviceInfo,
        ).toJsonValue()

        sealed class ResolutionType : JsonSerializable {
            companion object {
                private const val RESOLUTION_TYPE = "type"
                private const val BUTTON_ID = "button_id"
                private const val BUTTON_DESCRIPTION = "button_description"

                private const val BUTTON_CLICK = "button_click"
                private const val MESSAGE_CLICK = "message_click"
                private const val USER_DISMISSED = "user_dismissed"
                private const val TIMED_OUT = "timed_out"
                private const val INTERRUPTED = "interrupted"
                private const val CONTROL = "control"
                private const val AUDIENCE_EXCLUDED = "audience_check_excluded"
            }
            data class ButtonTap(val identifier: String, val description: String) : ResolutionType() {

                override fun toJsonValue(): JsonValue = jsonMapOf(
                    RESOLUTION_TYPE to BUTTON_CLICK,
                    BUTTON_ID to identifier,
                    BUTTON_DESCRIPTION to description
                ).toJsonValue()
            }
            data object MessageTap : ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to MESSAGE_CLICK).toJsonValue()
            }
            data object UserDismissed : ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to USER_DISMISSED).toJsonValue()
            }
            data object TimedOut : ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to TIMED_OUT).toJsonValue()
            }
            data object Interrupted : ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to INTERRUPTED).toJsonValue()
            }
            data object Control : ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to CONTROL).toJsonValue()
            }
            data object AudienceExcluded: ResolutionType() {
                override fun toJsonValue(): JsonValue = jsonMapOf(RESOLUTION_TYPE to AUDIENCE_EXCLUDED).toJsonValue()
            }
        }
    }
}
