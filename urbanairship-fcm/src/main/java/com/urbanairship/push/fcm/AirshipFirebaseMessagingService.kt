/* Copyright Airship and Contributors */
package com.urbanairship.push.fcm

import com.urbanairship.push.fcm.AirshipFirebaseIntegration.processMessageSync
import com.urbanairship.push.fcm.AirshipFirebaseIntegration.processNewToken
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Airship FirebaseMessagingService.
 */
public class AirshipFirebaseMessagingService public constructor() : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        processMessageSync(applicationContext, message)
    }

    override fun onNewToken(token: String) {
        processNewToken(applicationContext, token)
    }
}
