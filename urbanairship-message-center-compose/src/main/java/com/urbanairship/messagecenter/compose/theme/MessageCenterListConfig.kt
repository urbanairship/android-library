package com.urbanairship.messagecenter.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.R
import com.urbanairship.messagecenter.ui.view.MessageListAction
import java.util.Objects

@OptIn(ExperimentalMaterial3Api::class)
public class MessageCenterListConfig constructor(
    /** View background color */
    public val background: Color? = null,

    /** Message Center list empty state message view override */
    public val overrideEmptyListMessage: (@Composable () -> Unit)? = null,

    /** Whether to show dividers between items in the Message Center list */
    public val itemDividersEnabled: Boolean = true,

    /** Message Center list item divider inset start */
    public val itemDividerInset: Inset = Inset(),

    /** Message Center list item config */
    public val listItemConfig: ListItemConfig = ListItemConfig(),
    /** Message Center list top app bar colors */
    public val topBarColors: (@Composable () -> TopAppBarColors) = { TopAppBarDefaults.topAppBarColors() },

    /** Message Center list edit bar colors */
    public val editBarContainerColor: (@Composable () -> Color) = { BottomAppBarDefaults.containerColor },

    /** Message Center list edit bar content color */
    public val editBarContentColor: (@Composable () -> Color) = { contentColorFor(editBarContainerColor()) },

    /** Listener interface for responding to `MessageListView` events. */
    public val listener: Listener? = null
) {
    public data class Inset(
        public val start: Dp = 16.dp,
        public val end: Dp = 16.dp
    )

    public interface Listener {
        /** Called when the list enters or exits editing mode. */
        public fun onEditModeChanged(isEditing: Boolean)
        /** Called when a message is clicked. */
        public fun onShowMessage(message: Message)
        /** Called when the user has triggered an action in the UI. */
        public fun onAction(action: MessageListAction)
    }

    public class ListItemConfig(
        public val background: Color? = null,
        public val highlightBackground: Color? = null,
        public val minHeight: Dp = 64.dp,
        public val showThumbnails: Boolean = true,
        /** Placeholder for messages, shown while loading or if no thumbnail is set */
        public val placeholderIcon: @Composable () -> Unit = {
            Icon(
                painter = painterResource(R.drawable.ua_ic_thumbnail_placeholder_36),
                contentDescription = "Message")
        },
        public val unreadIndicator: @Composable (Boolean) -> Unit = { read ->
            Box(Modifier.padding(top = 8.dp)) {
                if (read) {
                    Spacer(Modifier.size(14.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

            }
        },
        public val checkBoxStyle: @Composable () -> CheckboxColors = { CheckboxDefaults.colors() },
        public val padding: PaddingValues = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 8.dp
        ),
        public val titleStyle: @Composable () -> TextStyle = { MaterialTheme.typography.titleMedium },
        public val subtitleStyle: @Composable () -> TextStyle = { MaterialTheme.typography.bodyMedium },
        public val dateStyle: @Composable () -> TextStyle = { MaterialTheme.typography.bodySmall },
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ListItemConfig

            if (background != other.background) return false
            if (minHeight != other.minHeight) return false
            if (padding != other.padding) return false
            if (showThumbnails != other.showThumbnails) return false
            if (placeholderIcon != other.placeholderIcon) return false
            if (unreadIndicator != other.unreadIndicator) return false
            if (checkBoxStyle != other.checkBoxStyle) return false
            if (titleStyle != other.titleStyle) return false
            if (subtitleStyle != other.subtitleStyle) return false
            if (dateStyle != other.dateStyle) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(background, minHeight, padding, showThumbnails, placeholderIcon,
                unreadIndicator, checkBoxStyle, titleStyle, subtitleStyle, dateStyle)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageCenterListConfig

        if (itemDividersEnabled != other.itemDividersEnabled) return false
        if (itemDividerInset != other.itemDividerInset) return false
        if (background != other.background) return false
        if (overrideEmptyListMessage != other.overrideEmptyListMessage) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(itemDividersEnabled, itemDividerInset, background, overrideEmptyListMessage)
    }
}


/*
<style name="UrbanAirship.MessageCenter.List.EditModeToolbar" parent="">
        <item name="android:elevation">16dp</item>
        <item name="android:minHeight">?minTouchTargetSize</item>
        <item name="android:background">?colorSurfaceContainer</item>
    </style>
 */
