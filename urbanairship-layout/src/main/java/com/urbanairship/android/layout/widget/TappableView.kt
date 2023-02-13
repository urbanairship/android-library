package com.urbanairship.android.layout.widget

import kotlinx.coroutines.flow.Flow

internal interface TappableView {
    fun taps(): Flow<Unit>
}
