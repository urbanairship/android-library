package com.urbanairship.messagecenter.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.core.R
import com.urbanairship.messagecenter.ui.MessageCenterMessageFragment.OnMessageDeletedListener

/** `Activity` that displays the Message Center message view. */
public open class MessageActivity : FragmentActivity() {

    private var currentMessageId: String? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("MessageActivity - unable to create Activity, takeOff not called.")
            finish()
            return
        }

        val messageId = savedInstanceState?.getString(MESSAGE_ID_KEY)
            ?: MessageCenter.parseMessageId(intent)

        if (messageId == null) {
            UALog.w("MessageActivity - unable to display message, messageId is null!")
            finish()
            return
        }

        // Show the message in the message Fragment
        showMessage(messageId)

        val contentView = findViewById<ViewGroup>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentView.setPadding(systemBars.left, systemBars.right, systemBars.left, systemBars.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(contentView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(MESSAGE_ID_KEY, currentMessageId)
    }

    protected override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MessageCenter.parseMessageId(intent)?.let { showMessage(it) }
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return false
    }

    private fun showMessage(messageId: String) {
        val current = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as MessageCenterMessageFragment?

        if (current == null || messageId != current.messageId) {
            val fragment = MessageCenterMessageFragment.newInstance(messageId, showNavIcon = true)

            supportFragmentManager.commitNow {
                replace(android.R.id.content, fragment, FRAGMENT_TAG)
            }

            fragment.onMessageDeletedListener = OnMessageDeletedListener {
                fragment.deleteMessage(it)

                val msg = resources.getQuantityString(R.plurals.ua_mc_description_deleted, 1, 1)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

                finish()
            }
        }
    }

    public companion object {
        private const val FRAGMENT_TAG = "MessageFragment"
        private const val MESSAGE_ID_KEY = "messageId"

        /**
         * Creates an `Intent` to display the message with the given [messageId].
         *
         * @param context The context. Used to set the package name for the intent and to resolve whether `MessageCenter.VIEW_MESSAGE_INTENT_ACTION` can be handled.
         * @param messageId The message ID to display.
         *
         * @return An `Intent` to display a message in `MessageActivity`.
         */
        @JvmStatic
        public fun createIntent(context: Context, messageId: String): Intent {
            val intent = Intent().setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(MessageCenter.MESSAGE_DATA_SCHEME, messageId, null))

            // Try VIEW_MESSAGE_INTENT_ACTION first
            intent.setAction(MessageCenter.VIEW_MESSAGE_INTENT_ACTION)

            // Fallback to our MessageActivity if no activity is found to handle our view action
            if (intent.resolveActivity(context.packageManager) == null) {
                intent.setClass(context, MessageActivity::class.java)
            }

            return intent
        }
    }
}
