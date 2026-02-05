package com.urbanairship.messagecenter.compose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Message Center color palette.
 *
 * @property background Message Center background color
 * @property surface Message Center surface color
 * @property accent Message Center accent color
 * @property divider Message Center divider color
 * @property messageListHighlight Message Center list item highlight color
 * @property messageListItemTitle Message Center list item title color
 * @property messageListItemSubtitle Message Center list item subtitle color
 * @property messageListItemDate Message Center list item date color
 * @property messageListItemCheckbox Message Center list item checkbox colors
 * @property messageListBackground Message Center list background color
 * @property listTopBar Message Center list top bar colors
 * @property messageTopBar Message Center message top bar colors
 * @property messageCenterError Message Center error color
 * @property messageCenterAlertLabel Message Center alert label color
 * @property messageCenterEditBar Message Center list edit bar colors
 * @property messageCenterPullToRefreshBackground Message Center pull to refresh background color
 * @property messageCenterPullToRefresh Message Center pull to refresh color
 * @property messageCenterEditBarContent Message Center list edit bar content color
 * @property messageErrorBackground Background color for error messages on the message screen
 * @property messageError Message Error text color
 * @property messageLoadingBackground Background color for a message loading screen
 * @property messageEmptyBackground Background color for empty messages
 * @property messageEmptyLabel Message center error label color
 */
@Immutable
public data class MessageCenterColors(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val divider: Color,
    val messageListHighlight: Color,
    val messageListItemTitle: Color,
    val messageListItemSubtitle: Color,
    val messageListItemDate: Color,
    val messageListItemCheckbox: CheckboxColors,
    val messageListBackground: Color,
    val listTopBar: TopAppBarColors,
    val messageTopBar: TopAppBarColors,
    val messageCenterError: Color,
    val messageCenterAlertLabel: Color,
    val messageCenterEditBar: Color,
    val messageCenterPullToRefreshBackground: Color,
    val messageCenterPullToRefresh: Color,
    val messageCenterEditBarContent: Color,
    val messageErrorBackground: Color,
    val messageError: Color,
    val messageLoadingBackground: Color,
    val messageEmptyBackground: Color,
    val messageEmptyLabel: Color,
) {
    public companion object {

        /**
         * Light theme default colors.
         *
         * @param background Message Center background color
         * @param surface Message Center surface color
         * @param accent Message Center accent color
         * @param textPrimary Primary text color
         * @param textSecondary Secondary text color
         * @param divider Message Center divider color
         * @param error Message Center error color
         */
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

        /**
         * Dark theme default colors.
         *
         * @param background Message Center background color
         * @param surface Message Center surface color
         * @param accent Message Center accent color
         * @param textPrimary Primary text color
         * @param textSecondary Secondary text color
         * @param divider Message Center divider color
         * @param error Message Center error color
         */
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

/**
 * Checkbox color palette.
 *
 * @property checkedCheckmarkColor Color of the checkmark when checked
 * @property uncheckedCheckmarkColor Color of the checkmark when unchecked
 * @property checkedBoxColor Color of the box when checked
 * @property uncheckedBoxColor Color of the box when unchecked
 * @property disabledCheckedBoxColor Color of the box when checked and disabled
 * @property disabledUncheckedBoxColor Color of the box when unchecked and disabled
 * @property disabledIndeterminateBoxColor Color of the box when indeterminate and disabled
 * @property checkedBorderColor Color of the border when checked
 * @property uncheckedBorderColor Color of the border when unchecked
 * @property disabledBorderColor Color of the border when disabled
 * @property disabledUncheckedBorderColor Color of the border when unchecked and disabled
 * @property disabledIndeterminateBorderColor Color of the border when indeterminate and disabled
 */
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

/**
 * Top app bar color palette.
 *
 * @property containerColor Color of the app bar container
 * @property scrolledContainerColor Color of the app bar container when scrolled
 * @property navigationIconContentColor Color of the navigation icon content
 * @property titleContentColor Color of the title content
 * @property actionIconContentColor Color of the action icon content
 */
@Immutable
public data class TopAppBarColors(
    val containerColor: Color,
    val scrolledContainerColor: Color,
    val navigationIconContentColor: Color,
    val titleContentColor: Color,
    val actionIconContentColor: Color
)
