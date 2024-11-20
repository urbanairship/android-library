/* Copyright Airship and Contributors */
package com.urbanairship.sample.inbox

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.messagecenter.ui.MessageCenterFragment
import com.urbanairship.sample.R

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
