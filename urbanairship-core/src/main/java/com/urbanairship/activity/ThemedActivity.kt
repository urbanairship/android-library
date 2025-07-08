/* Copyright Airship and Contributors */
package com.urbanairship.activity

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentActivity
import com.urbanairship.activity.AppCompatDelegateWrapper.Companion.create

/**
 * Activity that automatically uses the AppCompat support library if its available and the application
 * extends the app compat theme.
 */
public abstract class ThemedActivity : FragmentActivity() {

    private var delegate: AppCompatDelegateWrapper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isAppCompatAvailable(this)) {
            delegate = create(this)
        }

        delegate?.onCreate(savedInstanceState)

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        delegate?.onPostCreate(savedInstanceState)
    }

    override fun getMenuInflater(): MenuInflater {
        return delegate?.menuInflater ?: super.getMenuInflater()
    }

    override fun setContentView(@LayoutRes layoutResId: Int) {
        if (delegate != null) {
            delegate?.setContentView(layoutResId)
        } else {
            super.setContentView(layoutResId)
        }
    }

    override fun setContentView(view: View) {
        if (delegate != null) {
            delegate?.setContentView(view)
        } else {
            super.setContentView(view)
        }
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        if (delegate != null) {
            delegate?.setContentView(view, params)
        } else {
            super.setContentView(view, params)
        }
    }

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        if (delegate != null) {
            delegate?.addContentView(view, params)
        } else {
            super.addContentView(view, params)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        delegate?.onConfigurationChanged(newConfig)
    }

    override fun onStop() {
        super.onStop()
        delegate?.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        delegate?.onPostResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        delegate?.onDestroy()
    }

    override fun onTitleChanged(title: CharSequence, color: Int) {
        super.onTitleChanged(title, color)
        delegate?.setTitle(title)
    }

    override fun invalidateOptionsMenu() {
        if (delegate != null) {
            delegate?.invalidateOptionsMenu()
        } else {
            super.invalidateOptionsMenu()
        }
    }

    /**
     * Helper method to enable up navigation.
     *
     * @param enabled `true` to enable up navigation, otherwise `false`.
     */
    protected fun setDisplayHomeAsUpEnabled(enabled: Boolean) {
        val delegate = delegate ?: run {
            actionBar?.setDisplayHomeAsUpEnabled(enabled)
            actionBar?.setHomeButtonEnabled(enabled)
            return
        }

        delegate.supportActionBar?.setDisplayHomeAsUpEnabled(enabled)
        delegate.supportActionBar?.setHomeButtonEnabled(enabled)
    }

    protected fun hideActionBar() {
        val delegate = delegate ?: run {
            actionBar?.hide()
            return
        }

        delegate.supportActionBar?.hide()
    }

    public companion object {

        private var isAppCompatDependencyAvailable: Boolean? = null

        /**
         * Checks if AppCompat support library is both installed and available for the activity.
         *
         * @param activity The activity to check.
         * @return `true` if app compatibility is available for the activity, otherwise `false`.
         */
        public fun isAppCompatAvailable(activity: Activity): Boolean {
            val hasAppCompat = isAppCompatDependencyAvailable ?: run {
                val result = try {
                    // Play Services
                    Class.forName("androidx.appcompat.app.AppCompatDelegate")
                    true
                } catch (e: ClassNotFoundException) {
                    false
                }

                isAppCompatDependencyAvailable = result
                result
            }

            if (!hasAppCompat) {
                return false
            }

            val colorPrimary =
                activity.resources.getIdentifier("colorPrimary", "attr", activity.packageName)
            if (colorPrimary == 0) {
                return false
            }

            val a = activity.obtainStyledAttributes(intArrayOf(colorPrimary))
            val isAvailable = a.hasValue(0)
            a.recycle()

            return isAvailable
        }
    }
}
