/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.widget.FrameLayout

/**
 * The frame a `list_view` stack puts around each `list_item`, to carry its accessibility info.
 *
 * It holds exactly one item and takes that item's declared size, so a stack asks the item, not the
 * frame, whether it [fills what it's offered][fillsOfferedWidth].
 */
internal class ListItemFrame(context: Context) : FrameLayout(context)
