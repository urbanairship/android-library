package com.urbanairship.messagecenter.compose.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.urbanairship.messagecenter.compose.R

/**
 * Configuration options for the Message Center UI.
 *
 * @property showMessageListThumbnail Whether to show message thumbnails in the Message Center list.
 * @property messageListPlaceholderIcon Placeholder for messages, shown while loading or if no thumbnail is set.
 * @property messageListUnreadIndicator Message list unread indicator override.
 * @property messageCenterDividerEnabled Whether to show dividers between items in the Message Center list
 * @property messageCenterEmptyListMessage Message Center list empty state message view override.
 * @property messageLoadingView Overrides message loading view.
 * @property messageCenterListTitle Overrides the title shown in the Message Center List screen top bar. If `null`, the default localized string will be used.
 * @property canDeleteMessages Whether messages can be deleted from the Message Center. Setting this to `false` will hide any delete UI in the Message Center.
 */
@Immutable
public data class MessageCenterOptions(
    val showMessageListThumbnail: Boolean,
    val messageListPlaceholderIcon: @Composable () -> Unit,
    val messageListUnreadIndicator: @Composable (Boolean) -> Unit,
    val messageCenterDividerEnabled: Boolean,
    val messageCenterEmptyListMessage: (@Composable () -> Unit)? = null,
    val messageLoadingView: (@Composable () -> Unit)? = null,
    val messageCenterListTitle: String? = null,
    val canDeleteMessages: Boolean = true
) {
    public companion object {
        public fun defaults(): MessageCenterOptions = MessageCenterOptions(
            showMessageListThumbnail = true,
            messageCenterDividerEnabled = false,
            messageListPlaceholderIcon = {
                Box(
                    modifier = Modifier.size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ua_ic_thumbnail_placeholder_36),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).align(Alignment.Center)
                    )
                }
            },
            messageListUnreadIndicator = { read ->
                if (!read) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MsgCenterTheme.colors.accent)
                    )
                }
            },
        )
    }
}
