package com.urbanairship.messagecenter.compose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
public data class MessageCenterColors(
    /** Message Center background color */
    val background: Color,
    /** Message Center surface color */
    val surface: Color,
    /** Message Center accent color */
    val accent: Color,
    /** Message Center divider color */
    val divider: Color,
    /** Message Center list item highlight color */
    val messageListHighlight: Color,
    /** Message Center list item title color */
    val messageListItemTitle: Color,
    /** Message Center list item subtitle color */
    val messageListItemSubtitle: Color,
    /** Message Center list item date color */
    val messageListItemDate: Color,
    /** Message Center list item checkbox colors */
    val messageListItemCheckbox: CheckboxColors,
    /** Message Center list background color */
    val messageListBackground: Color,
    /** Message Center list top bar colors */
    val listTopBar: TopAppBarColors,
    /** Message Center message top bar colors */
    val messageTopBar: TopAppBarColors,
    /** Message Center error color */
    val messageCenterError: Color,
    /** Message Center alert label color */
    val messageCenterAlertLabel: Color,
    /** Message Center list edit bar colors */
    val messageCenterEditBar: Color,
    /** Message Center pull to refresh background color */
    val messageCenterPullToRefreshBackground: Color,
    /** Message Center pull to refresh color */
    val messageCenterPullToRefresh: Color,
    /** Message Center list edit bar content color */
    val messageCenterEditBarContent: Color,
    /** Background color for error messages on the message screen*/
    val messageErrorBackground: Color,
    /** Message Error text color */
    val messageError: Color,
    /** Background color for a message loading screen */
    val messageLoadingBackground: Color,
    /** Background color for empty messages */
    val messageEmptyBackground: Color,
    /** Message center error label color */
    val messageEmptyLabel: Color,
) {
    public companion object {

        public fun lightDefaults(
            background: Color = Color(0xFFF3F3F3),
            surface: Color = Color.White,
            accent: Color = Color(0xFF004BFF),
            textPrimary: Color = Color.Black,
            textSecondary: Color = Color(0xFF616161),
            divider: Color = Color(0xFF8D8C8D),
            error: Color = Color(0xFFB00020)
        ): MessageCenterColors = MessageCenterColors(
            background = background,
            surface = surface,
            accent = accent,
            divider = divider,
            messageListHighlight = Color.Gray.copy(alpha = 0.3f),
            messageListItemTitle = textPrimary,
            messageListItemSubtitle = textSecondary,
            messageListItemDate = textSecondary,
            messageListBackground = surface,
            messageListItemCheckbox = CheckboxColors(
                checkedCheckmarkColor = Color.White,
                uncheckedCheckmarkColor = Color.Transparent,
                checkedBoxColor = accent,
                uncheckedBoxColor = Color.Transparent,
                disabledCheckedBoxColor = background.copy(alpha = 0.7f),
                disabledUncheckedBoxColor = Color.Transparent,
                disabledIndeterminateBoxColor = background.copy(alpha = 0.7f),
                checkedBorderColor = Color.Gray,
                uncheckedBorderColor = Color.Gray,
                disabledBorderColor = Color.Gray.copy(alpha = 0.7f),
                disabledUncheckedBorderColor = Color.Gray.copy(alpha = 0.7f),
                disabledIndeterminateBorderColor = Color.Gray.copy(alpha = 0.7f)
            ),
            listTopBar = TopAppBarColors(
                containerColor = surface,
                scrolledContainerColor = surface,
                navigationIconContentColor = textPrimary,
                titleContentColor = textPrimary,
                actionIconContentColor = textPrimary
            ),
            messageTopBar = TopAppBarColors(
                containerColor = surface,
                scrolledContainerColor = background,
                navigationIconContentColor = textPrimary,
                titleContentColor = textPrimary,
                actionIconContentColor = textPrimary
            ),
            messageCenterError = error,
            messageCenterAlertLabel = textPrimary,
            messageCenterEditBar = surface,
            messageCenterEditBarContent = textPrimary,
            messageCenterPullToRefreshBackground = surface,
            messageCenterPullToRefresh = textPrimary,
            messageErrorBackground = surface,
            messageError = error,
            messageLoadingBackground = surface,
            messageEmptyBackground = surface,
            messageEmptyLabel = textPrimary,
        )

        public fun darkDefaults(
            background: Color = Color.Black,
            surface: Color = Color(0xFF121212),
            accent: Color = Color(0xFF1C5AFA),
            textPrimary: Color = Color.White,
            textSecondary: Color = Color(0xFFBDBDBD),
            divider: Color = Color(0xFF616161),
            error: Color = Color(0xFFB00020)
        ): MessageCenterColors = MessageCenterColors(
            background = background,
            surface = surface,
            accent = accent,
            divider = divider,
            messageListItemTitle = textPrimary,
            messageListItemSubtitle = textSecondary,
            messageListItemDate = textSecondary,
            messageListHighlight = Color.Gray,
            messageListItemCheckbox = CheckboxColors(
                checkedCheckmarkColor = Color.White,
                uncheckedCheckmarkColor = Color.Transparent,
                checkedBoxColor = accent,
                uncheckedBoxColor = Color.Transparent,
                disabledCheckedBoxColor = background.copy(alpha = 0.7f),
                disabledUncheckedBoxColor = Color.Transparent,
                disabledIndeterminateBoxColor = background.copy(alpha = 0.7f),
                checkedBorderColor = Color.Gray,
                uncheckedBorderColor = Color.Gray,
                disabledBorderColor = Color.Gray.copy(alpha = 0.7f),
                disabledUncheckedBorderColor = Color.Gray.copy(alpha = 0.7f),
                disabledIndeterminateBorderColor = Color.Gray.copy(alpha = 0.7f)
            ),
            listTopBar = TopAppBarColors(
                containerColor = surface,
                scrolledContainerColor = surface,
                navigationIconContentColor = textPrimary,
                titleContentColor = textPrimary,
                actionIconContentColor = textPrimary
            ),
            messageTopBar = TopAppBarColors(
                containerColor = surface,
                scrolledContainerColor = background,
                navigationIconContentColor = textPrimary,
                titleContentColor = textPrimary,
                actionIconContentColor = textPrimary
            ),
            messageListBackground = surface,
            messageCenterError = error,
            messageCenterAlertLabel = textPrimary,
            messageCenterEditBar = surface,
            messageCenterEditBarContent = textPrimary,
            messageCenterPullToRefreshBackground = surface,
            messageCenterPullToRefresh = textPrimary,
            messageErrorBackground = surface,
            messageError = error,
            messageLoadingBackground = surface,
            messageEmptyBackground = surface,
            messageEmptyLabel = textPrimary,
        )
    }
}

@Immutable
public data class CheckboxColors(
    val checkedCheckmarkColor: Color,
    val uncheckedCheckmarkColor: Color,
    val checkedBoxColor: Color,
    val uncheckedBoxColor: Color,
    val disabledCheckedBoxColor: Color,
    val disabledUncheckedBoxColor: Color,
    val disabledIndeterminateBoxColor: Color,
    val checkedBorderColor: Color,
    val uncheckedBorderColor: Color,
    val disabledBorderColor: Color,
    val disabledUncheckedBorderColor: Color,
    val disabledIndeterminateBorderColor: Color
)

@Immutable
public data class TopAppBarColors(
    val containerColor: Color,
    val scrolledContainerColor: Color,
    val navigationIconContentColor: Color,
    val titleContentColor: Color,
    val actionIconContentColor: Color
)
