/* Copyright Airship and Contributors */

package com.urbanairship.debug.push

import androidx.annotation.RestrictTo
import androidx.lifecycle.ViewModel
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList

/**
 * PushItem list view model.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PushListViewModel(repository: PushRepository) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
    }

    val pushes = LivePagedListBuilder(repository.getPushes(),
            PagedList.Config.Builder()
                    .setPageSize(PAGE_SIZE)
                    .setEnablePlaceholders(false)
                    .build())
            .build()
}
