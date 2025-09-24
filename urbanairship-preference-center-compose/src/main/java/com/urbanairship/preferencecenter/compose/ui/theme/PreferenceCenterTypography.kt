package com.urbanairship.preferencecenter.compose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
public data class PreferenceCenterTypography(
    val title: TextStyle,
    val description: TextStyle,
    val itemTitle: TextStyle,
    val itemDescription: TextStyle,
    val sectionTitle: TextStyle,
    val sectionDescription: TextStyle,
    val sectionLabel: TextStyle,
    val subscriptionTypeChipLabel: TextStyle,
    val alertTitle: TextStyle,
    val alertDescription: TextStyle,
    val alertButtonLabel: TextStyle,
    val contactManagementItemTitle: TextStyle,
    val contactManagementItemDescription: TextStyle,
    val contactManagementButtonLabel: TextStyle,
    val contactManagementDialogTitle: TextStyle,
    val contactManagementDialogDescription: TextStyle,
    val contactManagementDialogInputLabel: TextStyle,
    val contactManagementDialogInputHint: TextStyle,
    val contactManagementDialogDropdownItem: TextStyle
) {
    public companion object {

        public fun defaults(fontFamily: FontFamily? = null): PreferenceCenterTypography {

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

            return PreferenceCenterTypography(
                title = primaryText,
                description = primaryText,
                itemTitle = primaryText,
                itemDescription = secondaryText,
                sectionTitle = primaryText,
                sectionDescription = tertiaryText,
                sectionLabel = labelText,
                subscriptionTypeChipLabel = labelText,
                alertTitle = primaryText,
                alertDescription = secondaryText,
                alertButtonLabel = labelText,
                contactManagementItemTitle = tertiaryText,
                contactManagementItemDescription = tertiaryText,
                contactManagementButtonLabel = labelText,
                contactManagementDialogTitle = primaryText,
                contactManagementDialogDescription = tertiaryText,
                contactManagementDialogInputLabel = tertiaryText,
                contactManagementDialogInputHint = tertiaryText,
                contactManagementDialogDropdownItem = labelText
            )
        }
    }
}
