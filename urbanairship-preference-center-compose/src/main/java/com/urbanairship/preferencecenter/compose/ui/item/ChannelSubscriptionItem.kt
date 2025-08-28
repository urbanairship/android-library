package com.urbanairship.preferencecenter.compose.ui.item

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item

internal data class ChannelSubscriptionItem(
    val item: Item.ChannelSubscription
): BasePrefCenterItem(TYPE_PREF_CHANNEL_SUBSCRIPTION) {

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val title: String? = item.display.name
    val subtitle: String? = item.display.description

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as ChannelSubscriptionItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as ChannelSubscriptionItem

        if (title != otherItem.title) return false
        if (subtitle != otherItem.subtitle) return false
        if (subscriptionId != otherItem.subscriptionId) return false

        return true
    }
}

@Composable
internal fun ChannelSubscriptionItem.toView(
    isChecked: () -> Boolean,
    onCheckedChanged: (checked: Boolean) -> Unit
) {
    val context = LocalContext.current
    val item = this

    Row(Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp)
        .semantics {
            contentDescription = accessibilityDescription(
                context = context,
                item = item,
                isChecked = isChecked())
        },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column {
            title?.let { text ->
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            subtitle?.let { text ->
                Text(
                    text = text,
                    maxLines = 10,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Switch(
            modifier = Modifier.sizeIn(minWidth = 48.dp).padding(start = 16.dp),
            checked = isChecked(),
            onCheckedChange = onCheckedChanged
        )
    }
}

private fun accessibilityDescription(
    context: Context,
    item: ChannelSubscriptionItem,
    isChecked: Boolean
): String {
    return context.getString(
        R.string.ua_preference_center_subscription_item_description,
        item.title,
        item.subtitle,
        if (isChecked) {
            R.string.ua_preference_center_subscribed_description
        } else {
            R.string.ua_preference_center_unsubscribed_description
        }.let(context::getString)
    )
}

@Preview
@Composable
private fun preview() {
    ChannelSubscriptionItem(
        item = Item.ChannelSubscription(
            id = "preview id",
            subscriptionId = "preview subscription id",
            display = CommonDisplay(
                name = "preview name",
                description = "preview description"
            ),
            conditions = emptyList()
        )
    ).toView(
        isChecked = { true },
        onCheckedChanged = {}
    )
}
