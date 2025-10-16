package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityViewCommand.CommandArguments
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.urbanairship.Airship
import com.urbanairship.images.ImageLoader
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.MessageCenterFragment
import com.urbanairship.messagecenter.ui.view.MessageListView
import com.urbanairship.messagecenter.util.setTextOrHide
import com.urbanairship.util.AccessibilityUtils
import java.text.DateFormat
import com.urbanairship.R as coreR
import com.urbanairship.messagecenter.core.R as CoreR

/**
 * A `View` representing a message in the Message Center message list.
 *
 * @see MessageListView
 */
public class MessageListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = R.style.UrbanAirship_MessageCenter_Item
) : FrameLayout(
    context,
    attrs,
    defStyleAttr,
    defStyleRes
) {
    private var showThumbnails: Boolean = true
    private var placeholderRes: Int = R.drawable.ua_message_item_thumbnail_placeholder

    private var boundMessage: Message? = null

    private var isEditing: Boolean = false
    private var isSelected: Boolean = false
    private var isRead: Boolean = false
    private var isHighlighted: Boolean = false

    private val accessibilityActionIds = mutableListOf<Int>()

    /** Listener interface for accessibility actions set on `MessageListItem`. */
    public interface AccessibilityActionListener {
        /** Called when the "Mark as Read" action is triggered. */
        public fun onMarkRead(message: Message)
        /** Called when the "Delete" action is triggered. */
        public fun onDelete(message: Message)
    }

    /** Listener for accessibility actions set on this `MessageListItem`. */
    public var accessibilityActionListener: AccessibilityActionListener? = null

    init {
        inflate(context, R.layout.ua_view_message_list_item_content, this)

        context.withStyledAttributes(
            set = attrs,
            attrs = R.styleable.UrbanAirship_MessageCenter,
            defStyleAttr = 0,
            defStyleRes = R.style.UrbanAirship_MessageCenter
        ) {
            setIconsEnabled(getBoolean(
                R.styleable.UrbanAirship_MessageCenter_messageCenterIconsEnabled,
                false
            ))

            setPlaceholderIcon(getResourceId(
                R.styleable.UrbanAirship_MessageCenter_messageCenterPlaceholderIcon,
                R.drawable.ua_message_item_thumbnail_placeholder
            ))
        }
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val state = super.onCreateDrawableState(extraSpace + 1)
        if (isHighlighted) {
            mergeDrawableStates(state, STATE_HIGHLIGHTED)
        }
        return state
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)

        // When in editing mode, announce this view as a checkbox
        if (isEditing) {
            info.className = CheckBox::class.java.name
        }
    }

    private val views = Views(this)

    private val imageLoader: ImageLoader
        get() = Airship.imageLoader

    private val messageListItemAnimator = MessageListItemAnimator(
        context = context,
        unreadContainer = views.unreadContainer,
        checkable = views.checkable
    )

    //
    // List Item Binding / Content Updates
    //

    /** Binds the given [Message] to this view. */
    public fun bind(item: Message) {
        boundMessage = item

        setText(item)
        setThumbnail(item.listIconUrl)
        updateReadState(item.isRead)

        updateContentDescription(isSelected = views.checkable.isChecked)
        updateAccessibilityActions()
    }

    /** Sets the title, optional subtitle, and sent date. */
    private fun setText(item: Message) = with(views) {
        primaryText.text = item.title
        secondaryText.setTextOrHide(item.subtitle)
        tertiaryText.text = dateFormatter.format(item.sentDate)
    }

    /**
     * Load the thumbnail image if the theme allows it and the url is
     * not null/blank (with a placeholder during loading and on error).
     */
    private fun setThumbnail(url: String?) {
        if (this.showThumbnails && views.thumbnail != null) {
            if (url.isNullOrBlank().not()) {
                // Load image
                imageLoader.load(context, views.thumbnail, ImageRequestOptions.newBuilder(url)
                    .setPlaceHolder(placeholderRes)
                    .build()
                )
            } else {
                // Show thumbnail
                views.thumbnail.setImageResource(placeholderRes)
            }

            views.unreadContainer.isVisible = true
        }
    }

    /**
     * Updates the view's highlighted state.
     *
     * This represents the selected message that is currently being displayed (if this
     * `RecyclerView` is being displayed in [MessageCenterFragment] in a two-pane layout).
     *
     * @param highlighted `true` to highlight the view, `false` to remove the highlight.
     */
    internal fun updateHighlighted(highlighted: Boolean) {
        isHighlighted = highlighted
        refreshDrawableState()
    }

    /** Updates this view's read state. */
    private fun updateReadState(isRead: Boolean) {
        this.isRead = isRead

        // Change the text appearance of the title, based on read state
        TextViewCompat.setTextAppearance(views.primaryText, if (isRead) {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageTitle_Read
        } else {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageTitle_Unread
        })

        // Change the text appearance of the subtitle, based on read state
        TextViewCompat.setTextAppearance(views.secondaryText, if (isRead) {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageSubtitle_Read
        } else {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageSubtitle_Unread
        })

        // Change the text appearance of the sent date, based on read state
        TextViewCompat.setTextAppearance(views.tertiaryText, if (isRead) {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageSentDate_Read
        } else {
            R.style.UrbanAirship_MessageCenter_TextAppearance_MessageSentDate_Unread
        })

        // Show/hide unread indicator
        views.unreadIndicator.isGone = isRead

        // Update content description and a11y actions
        updateContentDescription(isRead = isRead)
        updateAccessibilityActions(isRead = isRead)
    }

    /** Updates this view's editing state, with an optional animation. */
    internal fun updateEditing(isEditing: Boolean, animate: Boolean = false) {
        this.isEditing = isEditing

        if (animate) {
            messageListItemAnimator.animateEditMode(isEditing)
        } else {
            views.unreadContainer.isGone = isEditing
            views.checkable.alpha = if (isEditing) 1f else 0f
            views.checkable.isVisible = isEditing
        }

        updateContentDescription(isEditing = isEditing)
        updateAccessibilityActions(isEditing = isEditing)
    }

    /** Updates this view's selected state. */
    internal fun updateSelected(isSelected: Boolean) {
        this.isSelected = isSelected

        views.checkable.isChecked = isSelected
        updateContentDescription(isSelected = isSelected)
        updateAccessibilityActions(isSelected = isSelected)
    }

    /** Updates the view's content description. */
    private fun updateContentDescription(
        isRead: Boolean = this.isRead,
        isEditing: Boolean = this.isEditing,
        isSelected: Boolean = this.isSelected
    ) {
        val message = boundMessage ?: return

        val sb = StringBuilder()
        // Selected state
        if (isEditing && isSelected) {
            sb.append(context.getString(CoreR.string.ua_mc_description_state_selected))
        }
        // Read state
        if (!isRead) {
            sb.append(context.getString(CoreR.string.ua_mc_description_state_unread))
        }
        // Title and date
        sb.append(
            context.getString(
                CoreR.string.ua_mc_description_title_and_date,
                message.title,
                dateFormatter.format(message.sentDate)
            )
        )

        contentDescription = sb.toString()
    }

    /** Updates the view's accessibility actions. */
    private fun updateAccessibilityActions(
        isEditing: Boolean = this.isEditing,
        isSelected: Boolean = this.isActivated,
        isRead: Boolean = this.isRead
    ) {
        // Clear any previously set actions to avoid duplicates.
        for (actionId in accessibilityActionIds) {
            ViewCompat.removeAccessibilityAction(this, actionId)
        }

        val message = boundMessage ?: return

        // Update click action to read "Tap to read message" instead of "Tap to activate".
        AccessibilityUtils.setClickActionLabel(this, CoreR.string.ua_mc_action_click)

        addAccessibilityAction(coreR.string.ua_delete) {
            accessibilityActionListener?.onDelete(message)
        }

        if (!isRead) {
            addAccessibilityAction(CoreR.string.ua_description_mark_read) {
                accessibilityActionListener?.onMarkRead(message)
            }
        }

        // When the list is loaded in MessageCenterActivity / MessageCenterFragment, we don't
        // expose edit mode if the list is in touch exploration mode, so we shouldn't get here
        // in normal operation. In order to make sure custom integrations are accessible, we'll
        // still update the click action to "select" or "unselect" when in edit mode here.
        if (isEditing) {
            // Add custom actions to support item selection on the item view.
            // This replaces checkbox/icon clicks when in screen reader mode.
            AccessibilityUtils.setClickActionLabel(this,
                if (isSelected) CoreR.string.ua_mc_action_unselect
                else CoreR.string.ua_mc_action_select
            )
        }
    }

    //
    // View Styling
    //

    /**
     * Sets whether or not the item should display thumbnails, and inflates the appropriate layout into the
     * checkable thumbnail container view.
     */
    public fun setIconsEnabled(showThumbnails: Boolean) {
        this.showThumbnails = showThumbnails

        val layout = if (showThumbnails) {
            R.layout.ua_view_message_list_item_checkable_thumbnail
        } else {
            R.layout.ua_view_message_list_item_checkable_no_thumbnail
        }
        val container: ViewGroup = findViewById(R.id.checkable_thumbnail_container)
        LayoutInflater.from(context).inflate(layout, container, true)
    }

    /**
     * Sets the placeholder drawable resource to use for thumbnails.
     */
    public fun setPlaceholderIcon(@DrawableRes placeholderRes: Int) {
        this.placeholderRes = placeholderRes
    }

    //
    // Helpers
    //

    /** Helper to add an accessibility action to this `View`. */
    private fun View.addAccessibilityAction(@StringRes labelRes: Int, block: () -> Unit) {
        val label = context.getString(labelRes)
        ViewCompat.addAccessibilityAction(this, label) { _: View?, _: CommandArguments? ->
            block.invoke()
            true
        }.let(accessibilityActionIds::add)
    }

    private data class Views(
        val root: View,
        val checkableThumbnailContainer: ViewGroup = root.findViewById(R.id.checkable_thumbnail_container),
        val unreadContainer: ViewGroup = root.findViewById(R.id.unread_container),
        val unreadIndicator: View = root.findViewById(R.id.unread_indicator),
        /** Thumbnail view. Will be null if the theme doesn't enable thumbnails. */
        val thumbnail: ImageView? = root.findViewById(R.id.thumbnail),
        val checkable: CheckBox = root.findViewById(R.id.checkable),
        val primaryText: TextView = root.findViewById(R.id.primaryText),
        val secondaryText: TextView = root.findViewById(R.id.secondaryText),
        val tertiaryText: TextView = root.findViewById(R.id.tertiaryText)
    )

    private companion object {
        private val dateFormatter = DateFormat.getDateInstance(DateFormat.LONG)

        private val STATE_HIGHLIGHTED: IntArray = intArrayOf(coreR.attr.ua_state_highlighted)
    }
}
