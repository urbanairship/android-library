package com.urbanairship.messagecenter.ui

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.urbanairship.messagecenter.R
import com.urbanairship.R as coreR

/** A [MessageListFragment] with a toolbar, used by [MessageCenterFragment]. */
public open class MessageCenterListFragment @JvmOverloads constructor(
    @LayoutRes contentLayoutId: Int = R.layout.ua_fragment_message_center_list
) : MessageListFragment(contentLayoutId) {

    private val a11yManager: AccessibilityManager by lazy {
        requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }

    public var isVerticalDividerVisible: Boolean = false
        set(value) {
            field = value
            verticalDivider?.isVisible = value
        }
        get() = verticalDivider?.isVisible ?: field

    private var verticalDivider: View? = null

    /** The `Toolbar` for the message list, or `null` if not yet inflated. */
    public var toolbar: Toolbar? = null

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

        var toolbarTitle = getString(coreR.string.ua_message_center_title)

        requireContext().withStyledAttributes(
            set = null,
            R.styleable.UrbanAirship_MessageCenter,
            defStyleAttr = 0,
            defStyleRes = R.style.UrbanAirship_MessageCenter
        ) {
            getString(R.styleable.UrbanAirship_MessageCenter_messageCenterToolbarTitle)?.let {
                toolbarTitle = it
            }
        }

        // Set up toolbar
        toolbar = view.findViewById<Toolbar>(R.id.toolbar).apply {
            setupToolbar(this)
            title = toolbarTitle
        }

        verticalDivider = view.findViewById<View?>(R.id.list_vertical_divider).apply {
            isVisible = isVerticalDividerVisible
        }

        // Listen for touch exploration changes
        a11yManager.addTouchExplorationStateChangeListener(::updateTouchExplorationEnabled)

        updateTouchExplorationEnabled()
    }

    override fun onStop() {
        super.onStop()

        a11yManager.removeTouchExplorationStateChangeListener(::updateTouchExplorationEnabled)
    }

    override fun onEditModeChanged(isEditing: Boolean): Unit = updateEditModeToggle(isEditing)

    /** Sets up the message list toolbar. */
    protected fun setupToolbar(toolbar: Toolbar): Unit = with(toolbar) {
        setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        updateTouchExplorationEnabled()

        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.enter_edit_mode -> {
                    updateEditMode(true)
                    true
                }
                R.id.leave_edit_mode -> {
                    updateEditMode(false)
                    true
                }
                R.id.mark_all_read -> {
                    markAllMessagesRead()
                    true
                }
                R.id.delete_all -> {
                    deleteAllMessages()
                    true
                }
                R.id.refresh -> {
                    refresh()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateTouchExplorationEnabled(
        isEnabled: Boolean = a11yManager.isTouchExplorationEnabled
    ) {
        val markAllReadItem = toolbar?.menu?.findItem(R.id.mark_all_read)
        val deleteAllItem = toolbar?.menu?.findItem(R.id.delete_all)

        if (isEnabled) {
            // Hide edit mode items
            toolbar?.menu?.run {
                findItem(R.id.enter_edit_mode)?.isVisible = false
                findItem(R.id.leave_edit_mode)?.isVisible = false
            }

            // Show alternate a11y actions
            markAllReadItem?.isVisible = true
            deleteAllItem?.isVisible = true
        } else {
            // Show edit mode items
            updateEditModeToggle(isEditing)

            // Hide alternate a11y actions
            markAllReadItem?.isVisible = false
            deleteAllItem?.isVisible = false
        }
    }

    private fun updateEditMode(editing: Boolean) {
        // Update super class editing state (updates the list view)
        isEditing = editing

        // We shouldn't get here because the edit toggle is disabled in touch exploration mode,
        // but just in case we do, or if a custom implementation gets here somehow, we should
        // still make sure we announce the UI change.

        // Announce edit mode
        val announcement = getString(
            if (editing) R.string.ua_announce_enter_edit_mode
            else R.string.ua_announce_leave_edit_mode
        )
        view?.announceForAccessibility(announcement)
    }

    private fun updateEditModeToggle(editing: Boolean) {
        toolbar?.menu?.run {
            findItem(R.id.enter_edit_mode)?.isVisible = !editing
            findItem(R.id.leave_edit_mode)?.isVisible = editing
        }
    }
}
