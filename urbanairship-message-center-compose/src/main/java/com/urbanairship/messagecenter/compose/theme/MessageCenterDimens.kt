package com.urbanairship.messagecenter.compose.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public data class MessageCenterDimens(
    val messageListItemMinHeight: Dp,
    val messageListItemPadding: PaddingValues,
    val messageListItemsSpace: Dp,
    /** Message Center list item divider inset start */
    public val messageCenterDividerInset: Inset,
) {
    public companion object {
        public fun defaults(): MessageCenterDimens = MessageCenterDimens(
            messageListItemMinHeight = 64.dp,
            messageListItemPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            messageListItemsSpace = 2.dp,
            messageCenterDividerInset = Inset(16.dp, 16.dp)
        )
    }

    public data class Inset(
        public val start: Dp,
        public val end: Dp
    )
}
