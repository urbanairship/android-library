/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

internal interface BaseView {
    interface VisibilityChangeListener {
        fun onVisibilityChanged(visibility: Int)
    }
}
