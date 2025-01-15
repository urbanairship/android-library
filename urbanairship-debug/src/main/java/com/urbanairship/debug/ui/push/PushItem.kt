package com.urbanairship.debug.ui.push

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushMessage
import com.urbanairship.util.UAStringUtil

/***
 * PushItem model object.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class PushItem(val pushEntity: PushEntity) {
    val message by lazy {
        PushMessage.fromJsonValue(JsonValue.parseString(pushEntity.payload))
    }

    val alert: String?
        get() {
            if (UAStringUtil.isEmpty(message.alert)) {
                return null
            }
            return message.alert
        }

    val time = pushEntity.time

    val pushId = pushEntity.pushId
}
