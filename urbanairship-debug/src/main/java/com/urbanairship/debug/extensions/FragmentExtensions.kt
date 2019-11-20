package com.urbanairship.debug.extensions

import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI

/**
 * Sets up the a toolbar with the navigation component.
 */
fun androidx.fragment.app.Fragment.setupToolbarWithNavController(@IdRes layoutId: Int): Toolbar? {
    var toolbar: Toolbar? = null
    view?.let {view ->
        toolbar = view.findViewById<Toolbar>(layoutId)
        toolbar?.let {
            NavigationUI.setupWithNavController(it, Navigation.findNavController(view))
        }
    }
    return toolbar
}