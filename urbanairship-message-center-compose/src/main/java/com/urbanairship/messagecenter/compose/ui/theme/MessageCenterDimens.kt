package com.urbanairship.messagecenter.compose.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Message Center dimensions.
 *
 * @param messageListItemMinHeight The minimum height for message list items
 * @param messageListItemPadding The padding for message list items
 * @param messageListItemsSpace The spacing between message list item title, description, and date text
 * @param messageCenterDividerInset The inset for message center list item dividers
 */
@Immutable
public data class MessageCenterDimens(
    val messageListItemMinHeight: Dp,
    val messageListItemPadding: PaddingValues,
    val messageListItemsSpace: Dp,
    val messageCenterDividerInset: Inset,
) {
    public companion object {

        /** Default Message Center dimensions */
        public fun defaults(): MessageCenterDimens = MessageCenterDimens(
            messageListItemMinHeight = 64.dp,
            messageListItemPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            messageListItemsSpace = 2.dp,
            messageCenterDividerInset = Inset(16.dp, 16.dp)
        )
    }

    /**
     * Inset values for start and end
     *
     * @param start The start inset
     * @param end The end inset
     */
    public data class Inset(
        public val start: Dp,
        public val end: Dp
    )
}
