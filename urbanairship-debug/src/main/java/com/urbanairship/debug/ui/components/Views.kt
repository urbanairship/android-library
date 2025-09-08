/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Section(
    modifier: Modifier = Modifier.fillMaxWidth(),
    title: String,
    accessory: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title.uppercase(), fontWeight = FontWeight.Light)
            accessory()
        }

        Spacer(modifier = Modifier.padding(bottom = 10.dp))

        content()
    }
}

@Composable
internal fun RowItem(
    modifier: Modifier = Modifier.fillMaxWidth(),
    title: String,
    addDivider: Boolean = true,
    details: String? = null,
    accessory: @Composable () -> Unit = {}
) {
    Column {
        ListItem(
            modifier = modifier,
            headlineContent = {
                Text(text = title, fontWeight = FontWeight.Medium)
            }, trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    details?.let { Text(text = it, fontSize = 14.sp, fontWeight = FontWeight.Light) }
                    accessory()
                }
            }
        )

        if (addDivider) {
            HorizontalDivider()
        }
    }
}

@Composable
internal fun LoadingView(modifier: Modifier = Modifier.width(64.dp)) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = modifier,
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteRow(content: @Composable () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when(it) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete() }
                else -> return@rememberSwipeToDismissBoxState false
            }
            return@rememberSwipeToDismissBoxState true
        },
        positionalThreshold = { it * .25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DismissBackground(dismissState = dismissState) }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier)

        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete"
        )
    }
}
