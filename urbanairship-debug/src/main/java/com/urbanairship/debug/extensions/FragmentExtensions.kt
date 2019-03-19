package com.urbanairship.debug.extensions

import android.support.annotation.IdRes
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI

/**
 * Sets up the a toolbar with the navigation component.
 */
fun Fragment.setupToolbarWithNavController(@IdRes layoutId: Int) {
    view?.let {view ->
        val toolbar = view.findViewById<Toolbar>(layoutId)
        toolbar?.let {
            NavigationUI.setupWithNavController(it, Navigation.findNavController(view))
        }
    }
}