package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageListView
import com.urbanairship.messagecenter.ui.view.MessageListView.OnShowMessageListener

/** `Fragment` that displays the Message Center list and message view. */
public open class MessageListFragment: Fragment(R.layout.ua_fragment_message_list) {

    private lateinit var messageListView: MessageListView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        messageListView = view.findViewById(R.id.message_list)
        super.onViewCreated(view, savedInstanceState)
    }

    public fun setPredicate(predicate: Predicate<Message>) {
        messageListView.predicate = predicate
    }

    public var isEditing: Boolean
        set(value) { messageListView.isEditing = value }
        get() = messageListView.isEditing

    public var onShowMessageListener: OnShowMessageListener?
        set(value) { messageListView.onShowMessageListener = value }
        get() = messageListView.onShowMessageListener


    public fun setHighlightedMessage(messageId: String) {
        messageListView.setHighlightedMessage(messageId)
    }
}
