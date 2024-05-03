/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage

import android.os.Bundle
import androidx.activity.addCallback
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.activity.ThemedActivity
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.assets.EmptyAirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.util.parcelableExtra

/**
 * In-app message activity.
 */
internal abstract class InAppMessageActivity<T: InAppMessageDisplayContent> : ThemedActivity() {

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

        val token = intent.getStringExtra(DISPLAY_LISTENER_TOKEN)

        displayContent = extractDisplayContent()

        assets = parcelableExtra<AirshipCachedAssets>(IN_APP_ASSETS) ?:
            parcelableExtra<EmptyAirshipCachedAssets>(IN_APP_ASSETS)


        if (token == null || displayContent == null) {
            UALog.e { "$javaClass unable to show message. Missing display handler or in-app message" }
            finish()
            return
        }

        displayListener = getDisplayListener(token)

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

    protected abstract fun getDisplayListener(token: String): InAppMessageDisplayListener?

    protected abstract fun extractDisplayContent(): T?

    override fun onResume() {
        super.onResume()
        displayListener?.onResume()
    }

    override fun onPause() {
        super.onPause()
        displayListener?.onPause()
    }

    companion object {
        /**
         * Display listener intent extra key.
         */
        const val DISPLAY_LISTENER_TOKEN = "analytics_token"

        /**
         * Message display content extra key.
         */
        const val DISPLAY_CONTENT = "display_content"

        /**
         * Assets intent extra key.
         */
        const val IN_APP_ASSETS = "assets"
    }
}
