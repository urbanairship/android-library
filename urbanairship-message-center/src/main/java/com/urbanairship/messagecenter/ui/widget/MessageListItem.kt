package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityViewCommand.CommandArguments
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.images.ImageLoader
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageListView
import com.urbanairship.messagecenter.ui.view.MessageCenterView
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.util.setTextOrHide
import com.urbanairship.util.AccessibilityUtils
import java.text.DateFormat

/**
 * A `View` representing a message in the Message Center message list.
 *
 * @see MessageListView
 */
public class MessageListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ConstraintLayout(
    context,
    attrs,
    defStyleAttr
) {
    private var showThumbnails: Boolean = false
    private var placeholderRes: Int = R.drawable.message_item_thumbnail_placeholder

    private var boundMessage: Message? = null

    private var isEditing: Boolean = false
    private var isSelected: Boolean = false
    private var isRead: Boolean = false
    private var isHighlighted: Boolean = false

    private val accessibilityActionIds = mutableListOf<Int>()

    init {
        inflate(context, R.layout.ua_view_message_list_item_content, this)

        // TODO(m3-inbox): fix this... it's not reading from the theme/style, probably because
        //      I'm missing something silly...
        context.withStyledAttributes(attrs, R.styleable.MessageCenter) {
            background = getDrawable(R.styleable.MessageCenter_messageCenterItemBackground)
                ?: context.getDrawable(R.drawable.message_list_item_background)

            setShowThumbnails(getBoolean(
                R.styleable.MessageCenter_messageCenterItemIconEnabled,
                true
            ))

            setThumbnailPlaceholder(getResourceId(
                R.styleable.MessageCenter_messageCenterItemIconPlaceholder,
                R.drawable.message_item_thumbnail_placeholder
            ))

            // TODO(m3-inbox): more styling

        }

        background = context.getDrawable(R.drawable.message_list_item_background)

        // TODO(m3-inbox): should probably load this from the theme
        with(resources) {
            // Set padding from dimens
            val top = getDimensionPixelSize(R.dimen.message_list_item_padding_top)
            val bottom = getDimensionPixelSize(R.dimen.message_list_item_padding_bottom)

            val (left, right) = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                getDimensionPixelSize(R.dimen.message_list_item_padding_end) to getDimensionPixelSize(R.dimen.message_list_item_padding_start)
            } else {
                getDimensionPixelSize(R.dimen.message_list_item_padding_start) to getDimensionPixelSize(R.dimen.message_list_item_padding_end)
            }

            setPadding(left, top, right, bottom)
        }
    }

    private val views = Views(this)

    private val imageLoader: ImageLoader
        get() = UAirship.shared().imageLoader

    private val messageListItemAnimator = MessageListItemAnimator(
        context = context,
        unreadContainer = views.unreadContainer,
        checkable = views.checkable
    )

    /**
     * Updates the view's highlighted state.
     *
     * This represents the selected message that is currently being displayed (if this
     * `RecyclerView` is being displayed in [MessageCenterView] in a two-pane master-detail layout).
     *
     * @param highlighted `true` to highlight the view, `false` to remove the highlight.
     */
    public fun updateHighlighted(highlighted: Boolean) {
        isHighlighted = highlighted

        // TODO(m3-inbox): update to properly set up selectors for the background state-list drawable
        val color = if (highlighted) R.color.ua_message_center_status_bar else android.R.color.transparent
        setBackgroundColor(ResourcesCompat.getColor(resources, color, context.theme))
        refreshDrawableState()
    }

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
        if (views.thumbnail != null) {
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

        // Show/hide unread indicator
        views.unreadIndicator.isGone = isRead

        // Update content description and a11y actions
        updateContentDescription(isRead = isRead)
        updateAccessibilityActions(isEditing = isEditing, isActivated = isSelected)
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
    }

    /** Updates this view's selected state. */
    internal fun updateSelected(isSelected: Boolean) {
        this.isSelected = isSelected

        views.checkable.isChecked = isSelected
        updateContentDescription(isSelected = isSelected)
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
            sb.append(context.getString(R.string.ua_mc_description_state_selected))
        }
        // Read state
        if (!isRead) {
            sb.append(context.getString(R.string.ua_mc_description_state_unread))
        }
        // Title and date
        sb.append(
            context.getString(
                R.string.ua_mc_description_title_and_date,
                message.title,
                dateFormatter.format(message.sentDate)
            )
        )

        contentDescription = sb.toString()
    }

    /** Updates the view's accessibility actions. */
    private fun updateAccessibilityActions(isEditing: Boolean, isActivated: Boolean) {
        // Clear any previously set actions to avoid duplicates.
        for (actionId in accessibilityActionIds) {
            ViewCompat.removeAccessibilityAction(this, actionId)
        }

        // Update click action to read "Tap to read message" instead of "Tap to activate".
        AccessibilityUtils.setClickActionLabel(this, R.string.ua_mc_action_click)

        if (isEditing) {
            // Add custom actions to support item selection on the item view.
            // This replaces checkbox/icon clicks when in screen reader mode.
            val actionLabel = context.getString(
                if (isActivated) R.string.ua_mc_action_unselect else R.string.ua_mc_action_select
            )
            val id = ViewCompat.addAccessibilityAction(this, actionLabel) { _: View?, _: CommandArguments? ->
                performClick()
                true
            }
            accessibilityActionIds.add(id)
        }
    }

    //
    // View Styling
    //

    /**
     * Sets whether or not the item should display thumbnails, and inflates the appropriate layout into the
     * checkable thumbnail container view.
     */
    public fun setShowThumbnails(showThumbnails: Boolean) {
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
    public fun setThumbnailPlaceholder(@DrawableRes placeholderRes: Int) {
        this.placeholderRes = placeholderRes
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

        @JvmStatic
        private val STATE_HIGHLIGHTED: IntArray = intArrayOf(com.urbanairship.R.attr.ua_state_highlighted)
    }
}
