package com.urbanairship.preferencecenter.compose.ui.item

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.contacts.Scope
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item

internal data class ContactSubscriptionItem(
    val item: Item.ContactSubscription
) : BasePrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION) {

    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val scopes: Set<Scope> = item.scopes
    val title: String? = item.display.name
    val subtitle: String? = item.display.description

    fun accessibilityDescription(context: Context, isChecked: Boolean): String {
        return context.getString(
            R.string.ua_preference_center_subscription_item_description,
            this.title,
            this.subtitle,
            if (isChecked) {
                R.string.ua_preference_center_subscribed_description
            } else {
                R.string.ua_preference_center_unsubscribed_description
            }.let(context::getString)
        )
    }
}

@Composable
internal fun ContactSubscriptionItem.Content(
    isChecked: (subscriptionId: String, scopes: Set<Scope>) -> Boolean,
    onCheckedChanged: (isChecked: Boolean) -> Unit
) {
    val context = LocalContext.current
    val isChecked = isChecked(subscriptionId, scopes)

    BaseSubscriptionItem(
        title = title,
        subtitle = subtitle,
        isChecked = { isChecked },
        onCheckedChanged = onCheckedChanged,
        accessibilityDescription = { accessibilityDescription(context, isChecked) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        ContactSubscriptionItem(
            item = Item.ContactSubscription(
                id = "id",
                subscriptionId = "preview subscription id",
                scopes = emptySet(),
                display = CommonDisplay(
                    name = "Preview name", description = "Preview description"
                ),
                conditions = emptyList()
            )
        ).Content(isChecked = { _, _ -> true }, onCheckedChanged = {})
    }
}
