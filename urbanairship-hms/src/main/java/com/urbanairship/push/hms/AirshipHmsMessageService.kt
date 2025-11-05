/* Copyright Airship and Contributors */
package com.urbanairship.push.hms

import com.urbanairship.push.hms.AirshipHmsIntegration.processMessageSync
import com.urbanairship.push.hms.AirshipHmsIntegration.processNewToken
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

/**
 * Airship HMS message service.
 */
public class AirshipHmsMessageService public constructor() : HmsMessageService() {

    override fun onMessageReceived(message: RemoteMessage) {
        processMessageSync(applicationContext, message)
    }

    override fun onNewToken(token: String?) {
        processNewToken(applicationContext, token)
    }
}
