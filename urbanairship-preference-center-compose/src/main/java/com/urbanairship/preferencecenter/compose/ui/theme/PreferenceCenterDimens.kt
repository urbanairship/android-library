package com.urbanairship.preferencecenter.compose.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Immutable
public data class PreferenceCenterDimens(
    val titlePadding: PaddingValues,
    val descriptionPadding: PaddingValues,
    val itemPadding: PaddingValues,
    val itemTitlePadding: PaddingValues,
    val itemDescriptionPadding: PaddingValues,
    val subscriptionSwitchMinWidth: Dp,
    val subscriptionSwitchPadding: PaddingValues,
    val subscriptionTypeChipMinHeight: Dp,
    val subscriptionTypeChipStoke: Dp,
    val subscriptionTypeChipSpacing: Dp,
    val subscriptionTypeChipCheckMarkSize: Dp,
    val subscriptionTypeChipCheckMarkOffsetX: Dp,
    val subscriptionTypeChipCheckMarkOffsetY: Dp,
    val subscriptionTypeChipCheckMarkStroke: Dp,
    val subscriptionTypeChipCheckMarkPadding: PaddingValues,
    val contactManagementItemMinHeight: Dp,
    val contactManagementItemTitlePadding: PaddingValues,
    val contactManagementItemIconSize: Dp,
    val contactManagementItemIconSpacing: Dp,
    val contactManagementItemActionSpacing: Dp,
    val contactManagementItemDeleteIconSize: Dp,
    val contactManagementDialogPadding: PaddingValues,
    val contactManagementDialogTitlePadding: PaddingValues,
    val contactManagementDialogDescriptionPadding: PaddingValues,
    val contactManagementDialogInputMinHeight: Dp,
    val contactManagementDialogInputPadding: PaddingValues,
    val contactManagementDialogFooterPadding: PaddingValues,
    val alertItemPadding: PaddingValues,
    val alertItemTitlePadding: PaddingValues,
    val alertItemDescriptionPadding: PaddingValues,
    val alertItemButtonPadding: PaddingValues,
    val alertIconPadding: PaddingValues,
    val alertIconSize: DpSize,
    val errorIconSize: DpSize,
) {
    public companion object {

        public fun defaults(): PreferenceCenterDimens = PreferenceCenterDimens(
            titlePadding = PaddingValues(vertical = 4.dp),
            descriptionPadding = PaddingValues(vertical = 4.dp),
            itemPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            itemTitlePadding = PaddingValues(vertical = 4.dp),
            itemDescriptionPadding = PaddingValues(0.dp),
            subscriptionSwitchMinWidth = 48.dp,
            subscriptionSwitchPadding = PaddingValues(start = 16.dp),
            subscriptionTypeChipMinHeight = 32.dp,
            subscriptionTypeChipStoke = 1.dp,
            subscriptionTypeChipSpacing = 4.dp,
            subscriptionTypeChipCheckMarkSize = 24.dp,
            subscriptionTypeChipCheckMarkOffsetX = (-3).dp,
            subscriptionTypeChipCheckMarkOffsetY = 0.dp,
            subscriptionTypeChipCheckMarkStroke = 1.dp,
            subscriptionTypeChipCheckMarkPadding = PaddingValues(all = 4.dp),
            contactManagementItemMinHeight = 48.dp,
            contactManagementItemTitlePadding = PaddingValues(bottom = 2.dp),
            contactManagementItemIconSize = 20.dp,
            contactManagementItemIconSpacing = 8.dp,
            contactManagementItemActionSpacing = 8.dp,
            contactManagementItemDeleteIconSize = 48.dp,
            contactManagementDialogPadding = PaddingValues(all = 16.dp),
            contactManagementDialogTitlePadding = PaddingValues(bottom = 16.dp),
            contactManagementDialogDescriptionPadding = PaddingValues(bottom = 16.dp),
            contactManagementDialogInputMinHeight = 48.dp,
            contactManagementDialogInputPadding = PaddingValues(vertical = 4.dp),
            contactManagementDialogFooterPadding = PaddingValues(vertical = 8.dp),
            alertItemPadding = PaddingValues(vertical = 8.dp),
            alertItemTitlePadding = PaddingValues(bottom = 4.dp),
            alertItemDescriptionPadding = PaddingValues(bottom = 4.dp),
            alertItemButtonPadding = PaddingValues(all = 0.dp),
            alertIconPadding = PaddingValues(all = 24.dp),
            alertIconSize = DpSize(width = 72.dp, height = 72.dp),
            errorIconSize = DpSize(width = 96.dp, height = 96.dp),
        )
    }
}
