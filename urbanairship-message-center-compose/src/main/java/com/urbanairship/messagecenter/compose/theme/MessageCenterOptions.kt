package com.urbanairship.messagecenter.compose.theme

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

@Immutable
public data class MessageCenterOptions(
    val showMessageListThumbnail: Boolean,
    /** Placeholder for messages, shown while loading or if no thumbnail is set */
    val messageListPlaceholderIcon: @Composable () -> Unit,
    /** Message list unread indicator override */
    val messageListUnreadIndicator: @Composable (Boolean) -> Unit,
    /** Whether to show dividers between items in the Message Center list */
    val messageCenterDividerEnabled: Boolean,
    /** Message Center list empty state message view override */
    val messageCenterEmptyListMessage: (@Composable () -> Unit)? = null,
    /** Overrides message loading view */
    val messageLoadingView: (@Composable () -> Unit)? = null,

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
                            .background(MessageCenterTheme.colors.accent)
                    )
                }
            },
        )
    }
}
