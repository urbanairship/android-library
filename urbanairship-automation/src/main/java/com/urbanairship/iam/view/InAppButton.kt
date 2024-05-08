package com.urbanairship.iam.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import androidx.annotation.RestrictTo

/**
 * `Button` subclass with no customization. Used to bypass inflation-time view replacement by
 * libraries like Material Components that can interfere with our ability to apply custom styling
 * in a consistent way.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class InAppButton : Button {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)
}
