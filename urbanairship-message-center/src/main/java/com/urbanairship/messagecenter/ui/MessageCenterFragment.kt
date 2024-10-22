package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageCenterView

/** `Fragment` that displays the Message Center list and message view. */
public open class MessageCenterFragment: Fragment(R.layout.ua_fragment_mc) {

    private lateinit var messageCenter: MessageCenterView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        messageCenter = view.findViewById(R.id.message_center)
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString(MESSAGE_ID)?.let(::displayMessage)
    }

    public var predicate: Predicate<Message>?
        get() = messageCenter.predicate
        set(value) { messageCenter.predicate = value }

    public fun displayMessage(messageId: String): Unit = messageCenter.displayMessage(messageId)
    public fun popMessageView(): Unit = messageCenter.popMessageView()

    public companion object {
        /** Argument key to specify the message */
        public const val MESSAGE_ID: String = "message_id"

        /**
         * Creates a new MessageCenterFragment
         *
         * @param messageId The message ID to display
         * @return messageFragment new [MessageCenterFragment]
         */
        @JvmStatic
        public fun newInstance(messageId: String?): MessageCenterFragment =
            MessageCenterFragment().apply {
                arguments = bundleOf(MESSAGE_ID to messageId)
            }
    }
}
