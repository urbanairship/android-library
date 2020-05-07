/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.urbanairship.debug.R
import com.urbanairship.iam.MediaInfo

class MediaInfoDetailsFragment : AutomationDetailsFragment() {

    companion object {
        const val ARGUMENT_MEDIA_INFO = "mediaInfo"
    }

    override fun createDetails(): LiveData<List<AutomationDetail>> {
        return requireArguments().parseJson(ARGUMENT_MEDIA_INFO) {
            MutableLiveData(mediaDetails(MediaInfo.fromJson(it)))
        }
    }

    private fun mediaDetails(mediaInfo: MediaInfo): List<AutomationDetail> {
        return mutableListOf<AutomationDetail>().apply {
            add(AutomationDetail(getString(R.string.ua_debug_type), mediaInfo.type))
            add(AutomationDetail(getString(R.string.ua_debug_url), mediaInfo.url))
            add(AutomationDetail(getString(R.string.ua_debug_description), mediaInfo.description))
        }
    }
}
