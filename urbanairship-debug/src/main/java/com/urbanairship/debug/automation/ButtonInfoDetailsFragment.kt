/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.iam.ButtonInfo
import com.urbanairship.iam.TextInfo
import com.urbanairship.json.JsonMap

class ButtonInfoDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_BUTTON_INFO = "buttonInfo"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        return requireArguments().parseJson(ARGUMENT_BUTTON_INFO) {
            MutableLiveData(buttonDetails(ButtonInfo.fromJson(it)))
        }
    }

    private fun navigateToTextInfo(textInfo: TextInfo) {
        val args = Bundle()
        args.putString(TextInfoDetailsFragment.ARGUMENT_TEXT_INFO, textInfo.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_buttonDetailsFragment_to_textInfoDetailsFragment, args)
    }

    private fun buttonDetails(button: ButtonInfo): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_debug_id), button.id))
            add(AutomationDetail(getString(R.string.ua_debug_label), button.label.text) {
                navigateToTextInfo(button.label)
            })

            add(AutomationDetail(getString(R.string.ua_debug_behavior), button.behavior))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_border_radius_key), button.borderRadius.toString()))

            button.backgroundColor?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_bg_color_key), it.toHex()))
            }

            button.borderColor?.let {
                add(AutomationDetail(getString(R.string.ua_debug_border_color), it.toHex()))
            }

            add(AutomationDetail(getString(R.string.ua_debug_actions), JsonMap(button.actions).toFormattedJsonString()))
        }
    }
}
