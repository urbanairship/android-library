/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.push

import android.arch.lifecycle.ViewModel
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.support.annotation.RestrictTo

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
