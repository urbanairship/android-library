/* Copyright Airship and Contributors */

package com.urbanairship.debug.push

import androidx.annotation.RestrictTo
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
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
