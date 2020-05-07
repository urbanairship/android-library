/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.urbanairship.debug.R
import com.urbanairship.iam.InAppMessage

class ScheduleUtils {
    companion object {
        @JvmStatic
        @ColorInt
        fun getColor(context: Context, type: String): Int {
            val colorRes = when (type) {
                InAppMessage.TYPE_BANNER -> R.color.ua_banner_iaa
                InAppMessage.TYPE_FULLSCREEN -> R.color.ua_fullscreen_iaa
                InAppMessage.TYPE_MODAL -> R.color.ua_modal_iaa
                InAppMessage.TYPE_HTML -> R.color.ua_html_iaa
                InAppMessage.TYPE_CUSTOM -> R.color.ua_custom_iaa
                else -> R.color.ua_custom_iaa
            }

            return ContextCompat.getColor(context, colorRes)
        }
    }
}
