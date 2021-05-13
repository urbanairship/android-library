package com.urbanairship.debug.deviceinfo.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreference
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import java.lang.IllegalArgumentException

class EnableFeaturePreference : SwitchPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    }

    val feature by lazy {
        when (key) {
            AirshipConfigOptions.FEATURE_ALL -> PrivacyManager.FEATURE_ALL
            AirshipConfigOptions.FEATURE_ANALYTICS -> PrivacyManager.FEATURE_ANALYTICS
            AirshipConfigOptions.FEATURE_CHAT -> PrivacyManager.FEATURE_CHAT
            AirshipConfigOptions.FEATURE_CONTACTS -> PrivacyManager.FEATURE_CONTACTS
            AirshipConfigOptions.FEATURE_IN_APP_AUTOMATION -> PrivacyManager.FEATURE_IN_APP_AUTOMATION
            AirshipConfigOptions.FEATURE_LOCATION -> PrivacyManager.FEATURE_LOCATION
            AirshipConfigOptions.FEATURE_MESSAGE_CENTER -> PrivacyManager.FEATURE_MESSAGE_CENTER
            AirshipConfigOptions.FEATURE_PUSH -> PrivacyManager.FEATURE_PUSH
            AirshipConfigOptions.FEATURE_TAGS_AND_ATTRIBUTES -> PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES
            else -> throw IllegalArgumentException("Invalid tag: $key")
        }
    }

    init {
        setDefaultValue(UAirship.shared().privacyManager.isEnabled(feature))
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        if (checked) {
            UAirship.shared().privacyManager.enable(feature)
        } else {
            UAirship.shared().privacyManager.disable(feature)
        }
    }

    override fun shouldPersist(): Boolean = false
}
