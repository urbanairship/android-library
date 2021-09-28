/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import com.urbanairship.UAirship
import com.urbanairship.util.UAStringUtil

/**
 * DialogPreference to set the named user.
 */
class NamedUserPreference(context: Context, attrs: AttributeSet) : EditTextPreference(context, attrs) {

    override fun setText(text: String?) {
        val namedUser = if (UAStringUtil.isEmpty(text?.trim())) null else text

        if (namedUser != null) {
            UAirship.shared().contact.identify(namedUser)
        } else {
            UAirship.shared().contact.reset()
        }

        notifyChanged()
    }

    override fun getText(): String? {
        return UAirship.shared().contact.namedUserId
    }

    override fun getSummary(): String? {
        return UAirship.shared().contact.namedUserId
    }

    override fun shouldPersist(): Boolean {
        return false
    }
}
