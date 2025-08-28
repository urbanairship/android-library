package com.urbanairship.preferencecenter.compose.ui.item

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbanairship.contacts.Scope
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item

internal data class ContactSubscriptionItem(
    val item: Item.ContactSubscription
) : BasePrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION) {

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val scopes: Set<Scope> = item.scopes
    val title: String? = item.display.name
    val subtitle: String? = item.display.description

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactSubscriptionItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactSubscriptionItem

        if (title != otherItem.title) return false
        if (subtitle != otherItem.subtitle) return false
        if (subscriptionId != otherItem.subscriptionId) return false
        if (scopes != otherItem.scopes) return false

        return true
    }
}

@Composable
internal fun ContactSubscriptionItem.toView(
    isChecked: (subscriptionId: String, scopes: Set<Scope>) -> Boolean,
    onCheckedChanged: (isChecked: Boolean) -> Unit
) {
    val context = LocalContext.current
    val isChecked = isChecked(subscriptionId, scopes)

    val accessibilityAction = context.getString(
        if (isChecked) {
            R.string.ua_preference_center_action_unsubscribe
        } else {
            R.string.ua_preference_center_action_subscribe
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            // Add a click listener on the whole item to provide a better experience for toggling subscriptions
            // when using screen readers.
            .clickable { onCheckedChanged(!isChecked) }
            .semantics {
                contentDescription = accessibilityLabel(context, isChecked)
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = accessibilityAction,
                        action = {
                            onCheckedChanged(!isChecked)
                            true
                        }
                    )
                )
            }
    ) {
        Column(Modifier.weight(1f)) {
            title?.let { values ->
                Text(
                    text = values,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            subtitle?.let { values ->
                Text(
                    text = values,
                    maxLines = 10,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        }

        Switch(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp)
                .padding(start = 16.dp),
            checked = isChecked,
            onCheckedChange = onCheckedChanged,
        )
    }
}

private fun ContactSubscriptionItem.accessibilityLabel(context: Context, isChecked: Boolean): String {
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

@Preview
@Composable
private fun preview() {
    ContactSubscriptionItem(
        item = Item.ContactSubscription(
            id = "id",
            subscriptionId = "preview subscription id",
            scopes = emptySet(),
            display = CommonDisplay(
                name = "preview name",
                description = "preview description"
            ),
            conditions = emptyList()
        )
    ).toView(
        isChecked = { _, _ -> true },
        onCheckedChanged = {}
    )
}
