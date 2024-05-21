/* Copyright Airship and Contributors */

package com.urbanairship.iam.legacy

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.push.InternalNotificationListener
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal sealed class LegacyInAppMessageUpdate {
    data class NewMessage(val message: LegacyInAppMessage): LegacyInAppMessageUpdate()

    data class DirectOpen(val sendId: String): LegacyInAppMessageUpdate()

    companion object {
        fun updates(pushManager: PushManager): Flow<LegacyInAppMessageUpdate> {
            return callbackFlow {
                val notificationListener = InternalNotificationListener { notificationInfo, _ ->
                    notificationInfo.message.sendId?.let {
                        this@callbackFlow.trySend(DirectOpen(it))
                    }
                }

                val pushListener = PushListener { message, _ ->
                    var legacyInAppMessage: LegacyInAppMessage? = null

                    try {
                        legacyInAppMessage = LegacyInAppMessage.fromPush(message)
                    } catch (e: IllegalArgumentException) {
                        UALog.e(e) {
                            "LegacyInAppMessageManager - Unable to create in-app message from push payload"
                        }
                    } catch (e: JsonException) {
                        UALog.e(e) {
                            "LegacyInAppMessageManager - Unable to create in-app message from push payload"
                        }
                    }

                    UALog.d { "Received a Push with an in-app message $legacyInAppMessage)" }

                    legacyInAppMessage?.let {
                        this@callbackFlow.trySend(NewMessage(it))
                    }
                }

                pushManager.addInternalNotificationListener(notificationListener)
                pushManager.addInternalPushListener(pushListener)

                awaitClose {
                    pushManager.removeInternalNotificationListener(notificationListener)
                    pushManager.removePushListener(pushListener)
                }
            }
        }
    }
}
