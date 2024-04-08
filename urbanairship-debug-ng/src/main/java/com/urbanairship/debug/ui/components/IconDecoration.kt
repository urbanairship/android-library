package com.urbanairship.debug.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A purely decorative icon, with no content description.
 *
 * *This should only be used for icons that are decorative and need no content description.*
 */
@Composable
internal fun IconDecoration(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(imageVector = imageVector, contentDescription = null, modifier = modifier, tint = tint)
}