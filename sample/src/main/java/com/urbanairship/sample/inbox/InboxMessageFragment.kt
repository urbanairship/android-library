/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.urbanairship.messagecenter.ui.MessageCenterMessageFragment
import com.urbanairship.messagecenter.ui.MessageCenterMessageFragment.OnMessageDeletedListener
import com.urbanairship.sample.MainActivity.TOP_LEVEL_DESTINATIONS
import com.urbanairship.sample.R
import com.urbanairship.messagecenter.R as messageCenterR

/** MessageFragment that supports navigation and overrides the default toolbar. */
class InboxMessageFragment : MessageCenterMessageFragment(R.layout.fragment_inbox_message) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.run {
            // Inflate the default menu
            inflateMenu(messageCenterR.menu.ua_message_center_message_pane_menu)

            val topLevelDestinations = AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build()
            setupWithNavController(findNavController(view), topLevelDestinations)
        }

        onMessageDeletedListener = OnMessageDeletedListener {
            findNavController().popBackStack()

            context?.run {
                val msg = getQuantityString(messageCenterR.plurals.ua_mc_description_deleted, 1, 1)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Gets a quantity string from resources and optionally formats it using the supplied arguments. */
private fun Context.getQuantityString(
    @PluralsRes pluralsId: Int,
    quantity: Int,
    vararg formatArgs: Any = emptyArray()
): String = with(resources) {
    if (formatArgs.isNotEmpty()) {
        getQuantityString(pluralsId, quantity, *formatArgs)
    } else {
        getQuantityString(pluralsId, quantity)
    }
}
