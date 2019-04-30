/* Copyright Airship and Contributors */

package com.urbanairship.debug.push

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
import android.support.annotation.RestrictTo
import com.urbanairship.debug.push.persistence.PushDao
import com.urbanairship.debug.push.persistence.PushEntity

/**
 * Event repository.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PushRepository(val dao: PushDao) {
    fun getPushes(): DataSource.Factory<Int, PushEntity> = dao.getPushes()
    fun getPush(pushId: String) : LiveData<PushEntity?> = dao.getPush(pushId)
}
