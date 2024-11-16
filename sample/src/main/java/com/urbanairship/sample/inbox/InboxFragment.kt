/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.messagecenter.ui.MessageCenterListFragment
import com.urbanairship.messagecenter.ui.MessageCenterMessageFragment
import com.urbanairship.messagecenter.ui.MessageListFragment.OnMessageClickListener
import com.urbanairship.sample.MainActivity.APP_BAR_CONFIGURATION
import com.urbanairship.sample.R
import com.urbanairship.messagecenter.R as messageCenterR

/** MessageCenterFragment that supports navigation and maintains its own toolbar. */
class InboxFragment() : MessageCenterListFragment(R.layout.fragment_inbox_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        setupWithNavController(toolbar, findNavController(), APP_BAR_CONFIGURATION)
        toolbar.inflateMenu(messageCenterR.menu.ua_message_center_list_pane_menu)

        onMessageClickListener = OnMessageClickListener {
            findNavController().navigate(
                R.id.action_messageCenterFragment_to_messageFragment, bundleOf(
                    MessageCenterMessageFragment.ARG_MESSAGE_ID to it.id,
                    MessageCenterMessageFragment.ARG_MESSAGE_TITLE to it.title
                )
            )
        }
    }
}
