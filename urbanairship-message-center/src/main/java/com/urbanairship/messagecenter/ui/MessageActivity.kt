package com.urbanairship.messagecenter.ui

import android.R
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.activity.ThemedActivity
import com.urbanairship.messagecenter.MessageCenter

/** `Activity` that displays the Message Center message view. */
public class MessageActivity : ThemedActivity() {

    private var currentMessageId: String? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            UALog.e("MessageActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        setDisplayHomeAsUpEnabled(true)

        val messageId = savedInstanceState
            ?.getString(MESSAGE_ID_KEY)
            ?: MessageCenter.parseMessageId(intent)

        if (messageId == null) {
            finish()
            return
        }

        displayMessage(messageId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(MESSAGE_ID_KEY, currentMessageId)
    }

    @SuppressLint("UnknownNullness")
    protected override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        MessageCenter.parseMessageId(intent)?.let { displayMessage(it) }
    }

    @SuppressLint("UnknownNullness")
    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                this.finish()
                return true
            }
        }
        return false
    }

    private fun displayMessage(messageId: String) {
        val current = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as MessageFragment?
        if (current == null || messageId != current.messageId) {
            val transaction = supportFragmentManager.beginTransaction()

            current?.let { transaction.remove(it) }

            transaction
                .add(R.id.content, MessageFragment.newInstance(messageId), FRAGMENT_TAG)
                .commitNow()
        }

        currentMessageId = messageId
    }

    internal companion object {
        private const val FRAGMENT_TAG = "MessageFragment"
        private const val MESSAGE_ID_KEY = "messageId"
    }
}
