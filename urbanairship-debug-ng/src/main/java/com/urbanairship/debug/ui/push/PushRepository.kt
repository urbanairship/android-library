package com.urbanairship.debug.ui.push

import androidx.annotation.RestrictTo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Push repository.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class PushRepository(val dao: PushDao) {
    fun getPushes(): MutableStateFlow<List<PushEntity>>  = MutableStateFlow(dao.getPushes())
    fun getPush(pushId: String): MutableStateFlow<PushEntity?> = MutableStateFlow(dao.getPush(pushId))
}