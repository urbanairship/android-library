/* Copyright Airship and Contributors */
package com.urbanairship.iam

import android.os.Bundle
import androidx.activity.addCallback
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.activity.ThemedActivity
import com.urbanairship.iam.adapter.InAppDisplayArgs
import com.urbanairship.iam.adapter.InAppDisplayArgsLoader
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.assets.EmptyAirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.util.parcelableExtra

/**
 * In-app message activity.
 */
internal abstract class InAppMessageActivity<T: InAppMessageDisplayContent> : ThemedActivity() {

    private lateinit var loader: InAppDisplayArgsLoader

    protected lateinit var args: InAppDisplayArgs<T>
        private set

    protected var displayContent: T? = null
        private set

    protected var displayListener: InAppMessageDisplayListener? = null
        private set

    protected var assets: AirshipCachedAssets? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        Autopilot.automaticTakeOff(this.applicationContext)
        super.onCreate(savedInstanceState)
        if (intent == null || intent.extras == null) {
            UALog.e { "Starting message activity with no intent" }
            finish()
            return
        }

        loader = parcelableExtra(EXTRA_DISPLAY_ARGS_LOADER) ?: run {
            UALog.e("Missing layout args loader")
            finish()
            return@onCreate
        }

        args = try {
            loader.load()
        } catch (e: InAppDisplayArgsLoader.LoadException) {
            UALog.e(e) { "Failed to load in-app message display args!" }
            finish()
            return
        }

        assets = args.assets ?: EmptyAirshipCachedAssets()
        displayListener = args.displayListener
        displayContent = args.displayContent

        if (displayListener?.isDisplaying == false) {
            finish()
            return
        }

        onCreateMessage(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            displayListener?.onUserDismissed()
            finish()
        }

        displayListener?.onAppear()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (displayListener?.isDisplaying == false) {
            finish()
            return
        }
    }

    /**
     * Called during [.onCreate] after the in-app message and display handler are parsed
     * from the intent.
     *
     * @param savedInstanceState The saved instance state.
     */
    protected abstract fun onCreateMessage(savedInstanceState: Bundle?)

    override fun onResume() {
        super.onResume()
        displayListener?.onResume()
    }

    override fun onPause() {
        super.onPause()
        displayListener?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::loader.isInitialized) {
            loader.dispose()
        }
    }

    companion object {
        // Asset loader
        const val EXTRA_DISPLAY_ARGS_LOADER: String =
            "com.urbanairship.automation.EXTRA_DISPLAY_ARGS_LOADER"
    }
}
