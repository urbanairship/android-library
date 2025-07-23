/* Copyright Airship and Contributors */
package com.urbanairship.activity

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatDelegate

/**
 * Wrapper for [AppCompatDelegate].
 */
internal class AppCompatDelegateWrapper {

    private lateinit var delegate: AppCompatDelegate

    fun onCreate(savedInstanceState: Bundle?) {
        delegate.installViewFactory()
        delegate.onCreate(savedInstanceState)
    }

    fun onPostCreate(savedInstanceState: Bundle?) = delegate.onPostCreate(savedInstanceState)

    val menuInflater: MenuInflater
        get() = delegate.menuInflater

    fun setContentView(layoutResId: Int) = delegate.setContentView(layoutResId)

    fun setContentView(view: View?) = delegate.setContentView(view)

    fun setContentView(view: View?, params: ViewGroup.LayoutParams?) =
        delegate.setContentView(view, params)

    fun addContentView(view: View?, params: ViewGroup.LayoutParams?) =
        delegate.addContentView(view, params)

    fun onConfigurationChanged(newConfig: Configuration?) = delegate.onConfigurationChanged(newConfig)

    fun onPostResume() = delegate.onPostResume()

    fun onStop() = delegate.onStop()

    fun invalidateOptionsMenu() = delegate.invalidateOptionsMenu()

    fun setTitle(title: CharSequence?) = delegate.setTitle(title)

    fun onDestroy() = delegate.onDestroy()

    val supportActionBar: ActionBar?
        get() = delegate.supportActionBar

    companion object {

        /**
         * Creates an `AppCompatDelegateWrapper`.
         *
         * @param activity The activity.
         * @return Instance of `AppCompatDelegateWrapper`.
         */
        fun create(activity: Activity): AppCompatDelegateWrapper {
            val delegateWrapper = AppCompatDelegateWrapper()
            delegateWrapper.delegate = AppCompatDelegate.create(activity, null)
            return delegateWrapper
        }
    }
}
