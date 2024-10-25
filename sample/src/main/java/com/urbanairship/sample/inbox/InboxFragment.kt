/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageCenterView
import com.urbanairship.messagecenter.ui.view.MessageListView
import com.urbanairship.sample.MainActivity.TOP_LEVEL_DESTINATIONS
import com.urbanairship.sample.R
import com.urbanairship.messagecenter.R as MessageCenterR

/**
 * MessageCenterFragment that supports navigation and maintains its own toolbar.
 */
class InboxFragment : Fragment(R.layout.fragment_inbox) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val messageCenter = view.findViewById<MessageCenterView>(R.id.message_center)

        val topLevelDestinations = AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build()

        setupWithNavController(toolbar, findNavController(), topLevelDestinations)

        with(toolbar) {
            inflateMenu(MessageCenterR.menu.message_list_menu)

            setOnMenuItemClickListener {
                when (it.itemId) {
                    MessageCenterR.id.toggle_edit_mode -> {
                        messageCenter.setListEditing(messageCenter.isListEditing.not())
                        true
                    }
                    MessageCenterR.id.refresh -> {
                        messageCenter.refreshMessages()
                        true
                    }
                    else -> false
                }
            }
        }

        messageCenter.listener = object : MessageCenterView.Listener {
            override fun onListEditingChanged(isEditing: Boolean) {
                val item = toolbar.menu.findItem(MessageCenterR.id.toggle_edit_mode)
                val iconRes = if (isEditing) {
                    MessageCenterR.drawable.ic_edit_off_24
                } else {
                    MessageCenterR.drawable.ic_edit_24
                }
                item.icon = ResourcesCompat.getDrawable(resources, iconRes, null)
            }

            override fun onShowMessage(message: Message): Boolean {
                return if (messageCenter.isTwoPane) {
                    false
                } else {
                    findNavController().navigate(
                        R.id.action_messageCenterFragment_to_messageFragment,
                        bundleOf(InboxMessageFragment.ARG_MESSAGE to message)
                    )
                    true
                }
            }

            override fun onCloseMessage() {
                // Pass
            }
        }
    }
}
