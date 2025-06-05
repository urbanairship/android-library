package com.urbanairship.android.layout.widget

/**
 * Interface for Views that support shrinking when placed in a container that doesn't have enough
 * room to fit all child content.
 */
internal interface ShrinkableView {

    /** Returns whether or not this view should be considered for shrinking. */
    fun isShrinkable(): Boolean
}
