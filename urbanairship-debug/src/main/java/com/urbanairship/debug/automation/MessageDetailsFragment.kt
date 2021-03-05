/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.iam.ButtonInfo
import com.urbanairship.iam.DisplayContent
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.MediaInfo
import com.urbanairship.iam.TextInfo
import com.urbanairship.iam.banner.BannerDisplayContent
import com.urbanairship.iam.custom.CustomDisplayContent
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent
import com.urbanairship.iam.html.HtmlDisplayContent
import com.urbanairship.iam.modal.ModalDisplayContent
import com.urbanairship.json.JsonMap
import java.util.concurrent.TimeUnit

class MessageDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_SCHEDULE = "schedule"
    }

    private fun navigate(textInfo: TextInfo) {
        val args = Bundle()
        args.putString(TextInfoDetailsFragment.ARGUMENT_TEXT_INFO, textInfo.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppDisplayContentDetailsFragment_to_textInfoDetailsFragment, args)
    }

    private fun navigate(buttonInfo: ButtonInfo) {
        val args = Bundle()
        args.putString(ButtonInfoDetailsFragment.ARGUMENT_BUTTON_INFO, buttonInfo.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppDisplayContentDetailsFragment_to_buttonDetailsFragment, args)
    }

    private fun navigate(mediaInfo: MediaInfo) {
        val args = Bundle()
        args.putString(MediaInfoDetailsFragment.ARGUMENT_MEDIA_INFO, mediaInfo.toJsonValue().toString())
        Navigation.findNavController(requireView())
                .navigate(R.id.action_inAppDisplayContentDetailsFragment_to_mediaInfoDetailsFragment, args)
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        var message = requireArguments().getParcelable<InAppMessage>(ARGUMENT_SCHEDULE)!!

        var details = when (message.getDisplayContent<DisplayContent>()) {
            is BannerDisplayContent -> bannerDetails(message.getDisplayContent()!!)
            is FullScreenDisplayContent -> fullScreenDetails(message.getDisplayContent()!!)
            is ModalDisplayContent -> modalDetails(message.getDisplayContent()!!)
            is HtmlDisplayContent -> htmlDetails(message.getDisplayContent()!!)
            is CustomDisplayContent -> customDetails(message.getDisplayContent()!!)
            else -> emptyList()
        }

        val list = mutableListOf<AutomationDetail>()
        list.add(AutomationDetail(getString(R.string.ua_iaa_debug_message_name_key), message.name.orEmpty()))
        list.add(AutomationDetail(getString(R.string.ua_iaa_debug_message_display_type_key), message.type.capitalize()))
        list.addAll(details)
        return MutableLiveData(list)
    }

    private fun bannerDetails(displayContent: BannerDisplayContent): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_placement_key), displayContent.placement))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_template_key), displayContent.template))

            displayContent.heading?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_heading_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.body?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_body_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.media?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_media_key), it.type) {
                    navigate(it)
                })
            }

            if (displayContent.actions.isNotEmpty()) {
                add(AutomationDetail(getString(R.string.ua_debug_click_actions), JsonMap(displayContent.actions).toFormattedJsonString()))
            }

            add(AutomationDetail(getString(R.string.ua_iaa_debug_duration_key), displayContent.duration.formatDuration(requireContext(), TimeUnit.MILLISECONDS)))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_border_radius_key), displayContent.borderRadius.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_bg_color_key), String.format("#%06X", 0xFFFFFF and displayContent.backgroundColor)))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_dismiss_button_color_key), String.format("#%06X", 0xFFFFFF and displayContent.dismissButtonColor)))

            addAll(buttonDetails(displayContent.buttons, displayContent.buttonLayout))
        }
    }

    private fun fullScreenDetails(displayContent: FullScreenDisplayContent): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {

            add(AutomationDetail(getString(R.string.ua_iaa_debug_template_key), displayContent.template))

            displayContent.heading?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_heading_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.body?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_body_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.footer?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_footer_key), it.label.text) {
                    navigate(it)
                })
            }

            displayContent.media?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_media_key), it.type) {
                    navigate(it)
                })
            }

            add(AutomationDetail(getString(R.string.ua_iaa_debug_bg_color_key), displayContent.backgroundColor.toHex()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_dismiss_button_color_key), displayContent.dismissButtonColor.toHex()))

            addAll(buttonDetails(displayContent.buttons, displayContent.buttonLayout))
        }
    }

    private fun modalDetails(displayContent: ModalDisplayContent): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_template_key), displayContent.template))

            displayContent.heading?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_heading_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.body?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_body_key), it.text) {
                    navigate(it)
                })
            }

            displayContent.footer?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_footer_key), it.label.text) {
                    navigate(it)
                })
            }

            displayContent.media?.let {
                add(AutomationDetail(getString(R.string.ua_iaa_debug_media_key), it.type) {
                    navigate(it)
                })
            }

            add(AutomationDetail(getString(R.string.ua_iaa_debug_border_radius_key), displayContent.borderRadius.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_bg_color_key), displayContent.backgroundColor.toHex()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_dismiss_button_color_key), displayContent.dismissButtonColor.toHex()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_fullscreen_key), displayContent.isFullscreenDisplayAllowed.toString()))
            addAll(buttonDetails(displayContent.buttons, displayContent.buttonLayout))
        }
    }

    private fun htmlDetails(displayContent: HtmlDisplayContent): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {

            add(AutomationDetail(getString(R.string.ua_debug_url), displayContent.url))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_requires_connectivity_key), displayContent.requireConnectivity.toString()))

            add(AutomationDetail(getString(R.string.ua_iaa_debug_width_key), displayContent.width.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_height_key), displayContent.height.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_keep_aspect_ratio_key), displayContent.aspectRatioLock.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_border_radius_key), displayContent.borderRadius.toString()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_bg_color_key), displayContent.backgroundColor.toHex()))
            add(AutomationDetail(getString(R.string.ua_iaa_debug_dismiss_button_color_key), displayContent.dismissButtonColor.toHex()))

            add(AutomationDetail(getString(R.string.ua_iaa_debug_fullscreen_key), displayContent.isFullscreenDisplayAllowed.toString()))
        }
    }

    private fun customDetails(displayContent: CustomDisplayContent): List<AutomationDetail> {
        return listOf(AutomationDetail(getString(R.string.ua_debug_value), displayContent.value.toFormattedJsonString()))
    }

    private fun buttonDetails(buttons: List<ButtonInfo>?, buttonLayout: String): List<AutomationDetail> {
        if (buttons.isNullOrEmpty()) {
            return emptyList()
        }

        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_iaa_debug_button_layout_key), buttonLayout))
            buttons.forEach {
                add(AutomationDetail(getString(R.string.ua_debug_button), it.label.text.orEmpty()) {
                    navigate(it)
                })
            }
        }
    }
}
