package com.urbanairship.messagecenter.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
public data class MessageCenterTypography(
    val itemTitle: TextStyle,
    val itemDescription: TextStyle,
    val itemDate: TextStyle,
    val emptyViewMessage: TextStyle,
    val messageCenterError: TextStyle,
    val alertButtonLabel: TextStyle,
    val messageError: TextStyle
) {
    public companion object {
        public fun defaults(fontFamily: FontFamily? = null): MessageCenterTypography {

            val primaryText = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )

            val secondaryText = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fontFamily
            )

            val tertiaryText = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fontFamily
            )

            val labelText = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )

            return MessageCenterTypography(
                itemTitle = primaryText,
                itemDescription = secondaryText,
                itemDate = tertiaryText,
                emptyViewMessage = secondaryText,
                messageCenterError = primaryText,
                alertButtonLabel = labelText,
                messageError = secondaryText
            )
        }
    }
}
