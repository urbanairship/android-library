package com.urbanairship.debug.extensions

import androidx.annotation.IdRes
import androidx.annotation.PluralsRes
import androidx.appcompat.widget.Toolbar
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI

/**
 * Sets up the a toolbar with the navigation component.
 */
fun androidx.fragment.app.Fragment.setupToolbarWithNavController(@IdRes layoutId: Int): Toolbar? {
    var toolbar: Toolbar? = null
    view?.let { view ->
        toolbar = view.findViewById(layoutId)
        toolbar?.let {
            NavigationUI.setupWithNavController(it, Navigation.findNavController(view))
        }
    }
    return toolbar
}

/**
 * Gets a quantity string from resources and optionally formats it using the supplied arguments.
 */
fun androidx.fragment.app.Fragment.getQuantityString(
    @PluralsRes pluralsId: Int,
    quantity: Int,
    vararg formatArgs: Any = emptyArray()
): String = if (formatArgs.isNotEmpty()) {
    resources.getQuantityString(pluralsId, quantity, *formatArgs)
} else {
    resources.getQuantityString(pluralsId, quantity)
}
