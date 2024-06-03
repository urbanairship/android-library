package com.urbanairship.debug.deviceinfo.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreference
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship

class EnableFeaturePreference : SwitchPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    }

    val feature by lazy {
        when (key) {
            AirshipConfigOptions.FEATURE_ALL -> PrivacyManager.Feature.ALL
            AirshipConfigOptions.FEATURE_ANALYTICS -> PrivacyManager.Feature.ANALYTICS
            AirshipConfigOptions.FEATURE_CONTACTS -> PrivacyManager.Feature.CONTACTS
            AirshipConfigOptions.FEATURE_IN_APP_AUTOMATION -> PrivacyManager.Feature.IN_APP_AUTOMATION
            AirshipConfigOptions.FEATURE_MESSAGE_CENTER -> PrivacyManager.Feature.MESSAGE_CENTER
            AirshipConfigOptions.FEATURE_PUSH -> PrivacyManager.Feature.PUSH
            AirshipConfigOptions.FEATURE_TAGS_AND_ATTRIBUTES -> PrivacyManager.Feature.TAGS_AND_ATTRIBUTES
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
