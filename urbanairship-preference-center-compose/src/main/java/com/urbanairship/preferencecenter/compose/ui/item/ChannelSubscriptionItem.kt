package com.urbanairship.preferencecenter.compose.ui.item

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item

internal data class ChannelSubscriptionItem(
    val item: Item.ChannelSubscription
): BasePrefCenterItem(TYPE_PREF_CHANNEL_SUBSCRIPTION) {

    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val title: String? = item.display.name
    val subtitle: String? = item.display.description

    fun accessibilityDescription(
        context: Context,
        isChecked: Boolean
    ): String {
        return context.getString(
            R.string.ua_preference_center_subscription_item_description,
            title,
            subtitle,
            if (isChecked) {
                R.string.ua_preference_center_subscribed_description
            } else {
                R.string.ua_preference_center_unsubscribed_description
            }.let(context::getString)
        )
    }
}

@Composable
internal fun ChannelSubscriptionItem.Content(
    isChecked: () -> Boolean,
    onCheckedChanged: (checked: Boolean) -> Unit
) {
    val context = LocalContext.current

    BaseSubscriptionItem(
        title = title,
        subtitle = subtitle,
        isChecked = isChecked,
        onCheckedChanged = onCheckedChanged,
        accessibilityDescription = { accessibilityDescription(context, isChecked()) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun preview() {
    PreferenceCenterTheme {
        ChannelSubscriptionItem(
            item = Item.ChannelSubscription(
                id = "preview id",
                subscriptionId = "preview subscription id",
                display = CommonDisplay(
                    name = "preview name", description = "preview description"
                ),
                conditions = emptyList()
            )
        ).Content(isChecked = { true }, onCheckedChanged = {})
    }
}
