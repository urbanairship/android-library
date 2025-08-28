package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
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

internal data class ContactSubscriptionGroupItem(
    val item: Item.ContactSubscriptionGroup
) : BasePrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP) {

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val title: String? = item.display.name
    val subtitle: String? = item.display.description
    val components: List<Item.ContactSubscriptionGroup.Component> = item.components

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactSubscriptionGroupItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactSubscriptionGroupItem

        if (title != otherItem.title) return false
        if (subscriptionId != otherItem.subscriptionId) return false
        if (components != otherItem.components) return false

        return true
    }
}

@Composable
internal fun ContactSubscriptionGroupItem.toView(
    isChecked: (subscriptionId: String, scopes: Set<Scope>) -> Boolean,
    onCheckedChange: (scopes: Set<Scope>, isChecked: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            title?.let { value ->
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            subtitle?.let { value ->
                Text(
                    text = value,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            FlowRow(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                components.forEach {
                    val isChecked = isChecked(subscriptionId, it.scopes)
                    var isFocused = false
                    FilterChip(
                        modifier = Modifier
                            .sizeIn(minHeight = dimensionResource(R.dimen.ua_preference_center_subscription_type_chip_min_height))
                            .onFocusChanged { event -> isFocused = event.isFocused },
                        selected = isChecked,
                        onClick = { onCheckedChange(it.scopes, !isChecked) },
                        leadingIcon = { componentsSelectedMark(isChecked) },
                        border = BorderStroke(
                            width = dimensionResource(
                                if (isFocused) R.dimen.ua_preference_center_subscription_type_chip_stroke_focused_width
                                else R.dimen.ua_preference_center_subscription_type_chip_stroke_width
                            ),
                            color = colorResource(R.color.ua_preference_center_subscription_type_chip_stroke)
                        ),
                        shape = MaterialTheme.shapes.large,
                        colors = FilterChipDefaults.filterChipColors().copy(
                            containerColor = Color(243, 243, 243),
                            selectedContainerColor = Color(243, 243, 243),
                        ),
                        label = {
                            it.display.name?.let { text ->
                                Text(
                                    text = text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun componentsSelectedMark(isChecked: Boolean) {
    val background = if(isChecked) colorResource(R.color.ua_preference_center_subscription_type_chip_checked_background)
                     else MaterialTheme.colorScheme.onTertiary
    Box(
        Modifier
            .size(24.dp)
            .offset(x = (-3).dp)
            .clip(CircleShape)
            .background(background)
            .border(
                width = 1.dp,
                color = colorResource(R.color.ua_preference_center_subscription_type_chip_stroke),
                shape = CircleShape
            )
    ) {
        if (isChecked) {
            Icon(
                modifier = Modifier.padding(4.dp),
                imageVector = Icons.Default.Check,
                tint = MaterialTheme.colorScheme.onTertiary,
                contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun preview() {
    ContactSubscriptionGroupItem(
        item = Item.ContactSubscriptionGroup(
            id = "id",
            subscriptionId = "subscriptionId",
            components = listOf(
                Item.ContactSubscriptionGroup.Component(
                    scopes = setOf(Scope.APP),
                    display = CommonDisplay("componentName 1", "componentDescription 1")
                ),
                Item.ContactSubscriptionGroup.Component(
                    scopes = setOf(Scope.SMS),
                    display = CommonDisplay("componentName 2", "componentDescription 2")
                )
            ),
            conditions = emptyList(),
            display = CommonDisplay(
                name = "name",
                description = "description",
            ),
        )
    ).toView(
        isChecked = { _, scopes -> scopes.contains(Scope.SMS) },
        onCheckedChange = { _, _ ->}
    )
}
