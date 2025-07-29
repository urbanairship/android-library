/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import androidx.core.app.NotificationManagerCompat

/**
 * Airship notification manager wrapper.
 *
 * @hide
 */
internal interface AirshipNotificationManager {

    enum class PromptSupport { NOT_SUPPORTED, COMPAT, SUPPORTED }

    fun areNotificationsEnabled(): Boolean

    fun areChannelsCreated(): Boolean

    val promptSupport: PromptSupport

    companion object {

        fun from(context: Context): AirshipNotificationManager {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            val targetSdkVersion = context.applicationInfo.targetSdkVersion

            return object : AirshipNotificationManager {
                override fun areNotificationsEnabled(): Boolean {
                    return notificationManagerCompat.areNotificationsEnabled()
                }

                override fun areChannelsCreated(): Boolean {
                    return notificationManagerCompat.notificationChannelsCompat.isNotEmpty()
                }

                override val promptSupport: PromptSupport
                    get() {
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
                                PromptSupport.SUPPORTED
                            } else {
                                PromptSupport.COMPAT
                            }
                        } else {
                            PromptSupport.NOT_SUPPORTED
                        }
                    }
            }
        }
    }
}
