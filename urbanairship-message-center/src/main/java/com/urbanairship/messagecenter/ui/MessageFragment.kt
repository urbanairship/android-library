/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageView

/** Fragment that displays a Message Center [Message]. */
// TODO(m3-inbox): now that we have message center Views, this doesn't need to be open, but we'll
//    need to refactor the sample and a11y message center implementations, as well as goat.
public open class MessageFragment public constructor() : Fragment(R.layout.ua_fragment_message) {

    /** The current [Message] ID. */
    public val messageId: String
        get() {
            return requireNotNull(arguments?.getString(MESSAGE_ID)) {
                "Missing required argument 'MESSAGE_ID'!"
            }
        }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messageView = view.findViewById<MessageView>(R.id.message)
        messageView.messageId = messageId
    }

    public companion object {
        /** Argument key to specify the message Reporting */
        public const val MESSAGE_ID: String = "messageReporting"

        /**
         * Creates a new MessageFragment
         *
         * @param messageId The message ID to display
         * @return messageFragment new MessageFragment
         */
        @JvmStatic
        public fun newInstance(messageId: String): MessageFragment =
            MessageFragment().apply {
                arguments = bundleOf(MESSAGE_ID to messageId)
            }
    }
}
