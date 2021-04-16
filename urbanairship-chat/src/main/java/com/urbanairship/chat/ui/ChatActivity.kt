package com.urbanairship.chat.ui

import android.os.Bundle
import android.view.MenuItem
import com.urbanairship.Autopilot
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.activity.ThemedActivity

class ChatActivity : ThemedActivity() {

    companion object {

        // TODO: make this actually work
        /**
         * Optional `String` extra specifying a message to pre-fill the message input box on launch.
         */
        const val EXTRA_DRAFT = "com.urbanairship.chat.EXTRA_DRAFT"

        private const val FRAGMENT_TAG = "CHAT_FRAGMENT"
    }

    private lateinit var fragment: ChatFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("ChatActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState != null) {
            fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as ChatFragment
        }

        if (!this::fragment.isInitialized) {
            fragment = ChatFragment()
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment, FRAGMENT_TAG)
                    .commitNow()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> false
        }
    }
}
