package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.google.android.material.appbar.CollapsingToolbarLayout

/** A [MessageListFragment] with a toolbar, used by [MessageCenterFragment]. */
public open class MessageCenterMessageFragment(
    @LayoutRes contentLayoutId: Int = R.layout.ua_fragment_message_center_message
) : MessageFragment(contentLayoutId) {

    /** Gets the message title from the arguments. */
    private val messageTitle: String? by lazy { arguments?.getString(ARG_MESSAGE_TITLE) }

    /** Gets the toolbar nav icon visibility from the arguments. */
    private val showNavIcon: Boolean? by lazy { arguments?.getBoolean(ARG_SHOW_NAV_ICON) }

    /** The `Toolbar` for the message view, or `null` if not yet inflated. */
    public var toolbar: Toolbar? = null
        set(value) {
            field = value
            updateToolbarNavIcon()
        }

    /**
     * Controls the visibility of the toolbar navigation icon.
     */
    public var isToolbarNavIconVisible: Boolean = showNavIcon ?: true
        set(value) {
            field = value
            updateToolbarNavIcon(value)
        }
        get() = (toolbar?.let { it.navigationIcon != null }  ?: field)

    /** Listener interface for message deletion. */
    public fun interface OnMessageDeletedListener {
        /** Called when a message is deleted. */
        public fun onDeleteMessage(message: Message)
    }

    /** Message deletion listener. */
    public var onMessageDeletedListener: OnMessageDeletedListener? = null

    /** Listener interface for message closure. */
    public fun interface OnMessageCloseListener {
        /** Called when a message requests to be closed. */
        public fun onCloseMessage()
    }

    /** Message close listener. */
    public var onMessageCloseListener: OnMessageCloseListener? = null

    private var collapseToolbar: CollapsingToolbarLayout? = null

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val themedContext = ContextThemeWrapper(
            inflater.context,
            R.style.UrbanAirship_MessageCenter
        )
        return inflater.cloneInContext(themedContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collapseToolbar = view.findViewById(R.id.collapse_toolbar)

        toolbar = view.findViewById<Toolbar>(R.id.toolbar).also {
            setupToolbar(it)
        }

        messageTitle?.let(::setToolbarTitle)

        updateToolbarNavIcon()
    }

    override fun onMessageLoaded(message: Message) {
        setToolbarTitle(message.title)
    }

    override fun onMessageLoadError(error: MessageViewState.Error.Type) {
        setToolbarTitle(null)
    }

    override fun onCloseMessage() {
        onMessageCloseListener?.onCloseMessage()
    }

    /** Sets the toolbar title. */
    public fun setToolbarTitle(title: String?) {
        collapseToolbar?.title = title
        toolbar?.title = title
    }

    /** Sets up the message toolbar. */
    protected fun setupToolbar(toolbar: Toolbar) {
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.delete -> {
                    currentMessage?.let {
                        onMessageDeletedListener?.onDeleteMessage(it)
                        deleteMessage(it)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarNavIcon(isVisible: Boolean = isToolbarNavIconVisible) {
        UALog.d { "Updating toolbar nav icon visibility: $isVisible" }
        if (isVisible) {
            toolbar?.setNavigationIcon(R.drawable.ua_ic_message_center_arrow_back)
        } else {
            toolbar?.navigationIcon = null
        }
    }

    public companion object {
        /** Required `String` argument for the message ID. */
        public const val ARG_MESSAGE_ID: String = com.urbanairship.messagecenter.ui.MessageFragment.ARG_MESSAGE_ID

        /** Optional `String` argument for the message title. */
        public const val ARG_MESSAGE_TITLE: String = "message_title"

        /** Optional `Boolean` argument for the toolbar nav icon visibility. */
        public const val ARG_SHOW_NAV_ICON: String = "show_nav_icon"

        /**
         * Creates a new instance of [MessageCenterMessageFragment].
         *
         * @param messageId The message ID to display.
         * @param messageTitle Optional message title to display. If `null`, the title will be loaded from the message.
         * @param showNavIcon Optional flag to show the toolbar navigation icon. If `null`, the icon will be shown.
         *
         * @return A new instance of [MessageCenterMessageFragment].
         */
        @JvmStatic
        @JvmOverloads
        public fun newInstance(
            messageId: String,
            messageTitle: String? = null,
            showNavIcon: Boolean? = null
        ): MessageCenterMessageFragment = MessageCenterMessageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MESSAGE_ID, messageId)
                messageTitle?.let { putString(ARG_MESSAGE_TITLE, it) }
                showNavIcon?.let { putBoolean(ARG_SHOW_NAV_ICON, it) }
            }
        }
    }
}
