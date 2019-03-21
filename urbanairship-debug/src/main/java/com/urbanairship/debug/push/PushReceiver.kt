package com.urbanairship.debug.push

import android.content.Context
import com.urbanairship.AirshipReceiver
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.push.persistence.PushEntity
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Captures received push messages.
 */
class PushReceiver : AirshipReceiver() {

    override fun onPushReceived(context: Context, message: PushMessage, notificationPosted: Boolean) {
        super.onPushReceived(context, message, notificationPosted)

        GlobalScope.launch(Dispatchers.IO) {
            ServiceLocator.shared(context).getPushDao().insertPush(PushEntity(message))
        }
    }
}
