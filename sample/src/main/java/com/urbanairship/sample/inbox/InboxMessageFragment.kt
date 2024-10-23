/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageView
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.urbanairship.sample.MainActivity.TOP_LEVEL_DESTINATIONS
import com.urbanairship.sample.R

/**
 * MessageFragment that supports navigation and maintains its own toolbar.
 */
class InboxMessageFragment : Fragment(R.layout.fragment_inbox_message) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val messageView = view.findViewById<MessageView>(R.id.message_view)

        val topLevelDestinations = AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build()

        setupWithNavController(toolbar, findNavController(view), topLevelDestinations)

        val args = arguments ?: Bundle()

        // Get the message from the arguments, if available
        @Suppress("DEPRECATION")
        val message = (args.getParcelable(ARG_MESSAGE) as? Message) ?: throw(
            IllegalArgumentException("InboxMessageFragment is missing required argument: $ARG_MESSAGE")
        )

        toolbar?.title = message.title

        // Set the message ID on the message view
        messageView.messageId = message.id
    }

    companion object {
        internal const val ARG_MESSAGE = "message"
    }
}
