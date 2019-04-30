/* Copyright Airship and Contributors */

package com.urbanairship.debug.push

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import android.support.annotation.RestrictTo
import com.urbanairship.debug.push.persistence.PushEntity
import org.json.JSONObject

/**
 * View model for event details.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PushDetailsViewModel(repository: PushRepository, val pushId: String) : ViewModel() {

    private val pushEntity: LiveData<PushEntity?> = repository.getPush(pushId)

    fun pushData(): LiveData<String> {
        return Transformations.map(pushEntity) {
            if (it == null) {
                "$pushId not found"
            } else {
                JSONObject(it.payload).toString(4)
            }
        }
    }
}
