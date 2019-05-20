package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v7.preference.Preference
import android.util.AttributeSet
import android.widget.Toast

import com.urbanairship.UAirship
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ClipboardAction
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.copyToClipboard

class UserPreference : Preference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun onClick() {
        super.onClick()
        summary.copyToClipboard(context, true)
    }

    override fun getSummary(): CharSequence {
        return UAirship.shared().inbox.user.id ?: context.getString(R.string.ua_none)
    }
}
