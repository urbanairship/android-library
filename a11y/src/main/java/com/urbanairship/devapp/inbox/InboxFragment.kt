/* Copyright Airship and Contributors */
package com.urbanairship.devapp.inbox

import android.os.Bundle
import android.view.View
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.messagecenter.ui.MessageCenterFragment

/**
 * MessageCenterFragment that supports navigation and maintains its own toolbar.
 */
class InboxFragment : MessageCenterFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listFragment?.toolbar?.let {
            setupWithNavController(it, findNavController(view))
        }
    }
}
