package com.urbanairship.preferencecenter.compose.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
public data class PreferenceCenterShapes(
    val sectionLabel: CornerBasedShape,
    val subscriptionTypeChip: CornerBasedShape,
    val subscriptionTypeChipCheckMark: CornerBasedShape,
    val contactManagementAddButton: CornerBasedShape,
    val contactManagementDialog: CornerBasedShape,
) {
    public companion object {

        public fun defaults(): PreferenceCenterShapes = PreferenceCenterShapes(
            sectionLabel = RoundedCornerShape(16.0.dp),
            subscriptionTypeChip = CircleShape,
            subscriptionTypeChipCheckMark = CircleShape,
            contactManagementAddButton = CircleShape,
            contactManagementDialog = RoundedCornerShape(16.0.dp)
        )
    }
}
