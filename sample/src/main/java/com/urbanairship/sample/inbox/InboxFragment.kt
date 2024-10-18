/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.urbanairship.messagecenter.ui.view.MessageCenterView
import com.urbanairship.sample.R

/**
 * MessageCenterFragment that supports navigation and maintains its own toolbar.
 */
// TODO(m3-inbox): Update to show MessageListView and keep InboxMessageFragment for MessageView,
//   or keep using MessageCenterView and delete InboxMessageFragment, so we can use our own toolbar?
class InboxFragment : Fragment(R.layout.fragment_inbox) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
//        NavigationUI.setupWithNavController(
//            toolbar,
//            findNavController(view),
//            AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build()
//        )

        val messageCenterView = view.findViewById<MessageCenterView>(R.id.message_center)

        ViewCompat.setOnApplyWindowInsetsListener(messageCenterView) { v: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as MarginLayoutParams
            lp.topMargin = insets.top
            lp.leftMargin = insets.left
            lp.rightMargin = insets.right
            v.layoutParams = lp
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(messageCenterView)
    }
}
