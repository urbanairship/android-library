package com.urbanairship.messagecenter.ui

import android.R
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import com.urbanairship.Autopilot
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter

/** `Activity` that displays the Message Center list and message view. */
public class MessageCenterActivity : FragmentActivity() {

    private lateinit var messageCenterFragment: MessageCenterFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            UALog.e("MessageCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        var fragment: MessageCenterFragment? = null

        if (savedInstanceState != null) {
            fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? MessageCenterFragment
        }

        if (fragment == null) {
            fragment = MessageCenterFragment.newInstance(MessageCenter.parseMessageId(intent))

            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, fragment, FRAGMENT_TAG)
                .commitNow()
        }

        messageCenterFragment = fragment

        // Apply the default message center predicate
        messageCenterFragment.predicate = MessageCenter.shared().predicate
    }

    public var predicate: Predicate<Message>?
        get() = messageCenterFragment.predicate
        set(value) { messageCenterFragment.predicate = value }

    public fun displayMessage(messageId: String): Unit = messageCenterFragment.displayMessage(messageId)

    public fun closeMessagePane(): Unit = messageCenterFragment.closeMessagePane()

    protected override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MessageCenter
            .parseMessageId(intent)
            ?.let(::displayMessage)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                this.finish()
                return true
            }
        }
        return false
    }

    internal companion object {
        private const val FRAGMENT_TAG = "MESSAGE_CENTER_FRAGMENT"
    }
}
