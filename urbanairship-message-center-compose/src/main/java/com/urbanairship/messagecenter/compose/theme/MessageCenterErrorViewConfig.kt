package com.urbanairship.messagecenter.compose.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Objects

public class MessageCenterErrorViewConfig(
    public val backgroundColor: Color? = null,
    public val padding: PaddingValues? = PaddingValues(0.dp)
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageCenterErrorViewConfig

        if (backgroundColor != other.backgroundColor) return false
        if (padding != other.padding) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(backgroundColor, padding)
    }
}
