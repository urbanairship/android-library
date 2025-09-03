package com.urbanairship.debug.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DebugCategoryHeader(
    text: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 16.dp),
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/**
 * Generic debug menu item.
 *
 * If [currentValue] and [description] are both provided, [currentValue] will be displayed.
 */
@Composable
internal fun DebugSettingItem(
    title: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
    description: String? = null,
    icon: ImageVector? = null,
    currentValue: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = onClick?.let {
        Modifier.clickable { it() }
    } ?: Modifier

    Surface(
        modifier = modifier.then(clickModifier)
    ) {
        ListItem(
            headlineContent = {
                Text(text = title, fontWeight = FontWeight.Medium)
            }, supportingContent = {
                (currentValue ?: description)?.let { Text(it) }
            }, trailingContent = {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        )
    }
}
