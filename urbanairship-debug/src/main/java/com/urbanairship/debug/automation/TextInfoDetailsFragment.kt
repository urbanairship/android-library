/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.urbanairship.debug.R
import com.urbanairship.iam.TextInfo

class TextInfoDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_TEXT_INFO = "textInfo"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        return requireArguments().parseJson(ARGUMENT_TEXT_INFO) {
            MutableLiveData(textInfoDetails(TextInfo.fromJson(it)))
        }
    }

    private fun textInfoDetails(textInfo: TextInfo): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_debug_text), textInfo.text))
            add(AutomationDetail(getString(R.string.ua_debug_font_size), textInfo.fontSize.toString()))

            if (textInfo.fontFamilies.isNotEmpty()) {
                add(AutomationDetail(getString(R.string.ua_debug_font_family), textInfo.fontFamilies.joinToString(", ")))
            }

            if (textInfo.styles.isNotEmpty()) {
                add(AutomationDetail(getString(R.string.ua_debug_styles), textInfo.styles.joinToString(", ")))
            }

            textInfo.alignment?.let {
                add(AutomationDetail(getString(R.string.ua_debug_alignment), textInfo.alignment))
            }

            textInfo.color?.let {
                add(AutomationDetail(getString(R.string.ua_debug_color), it.toHex()))
            }
        }
    }
}
