package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement.spacedBy
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.contacts.Scope
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item

internal data class ContactSubscriptionGroupItem(
    val item: Item.ContactSubscriptionGroup
) : BasePrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP) {

    override val conditions: Conditions = item.conditions

    val subscriptionId: String = item.subscriptionId
    val title: String? = item.display.name
    val subtitle: String? = item.display.description
    val components: List<Item.ContactSubscriptionGroup.Component> = item.components
}

@Composable
internal fun ContactSubscriptionGroupItem.Content(
    isChecked: (subscriptionId: String, scopes: Set<Scope>) -> Boolean,
    onCheckedChange: (scopes: Set<Scope>, isChecked: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(PrefCenterTheme.dimens.itemPadding)
    ) {
        Column {
            title?.let { value ->
                Text(
                    text = value,
                    style = PrefCenterTheme.typography.itemTitle,
                    color = PrefCenterTheme.colors.itemTitleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.itemTitlePadding)
                )
            }

            subtitle?.let { value ->
                Text(
                    text = value,
                    style = PrefCenterTheme.typography.itemDescription,
                    color = PrefCenterTheme.colors.itemDescriptionText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.itemDescriptionPadding)
                )
            }

            FlowRow(
                horizontalArrangement = spacedBy(PrefCenterTheme.dimens.subscriptionTypeChipSpacing)
            ) {
                components.forEach {
                    val isChecked = isChecked(subscriptionId, it.scopes)

                    FilterChip(
                        modifier = Modifier.sizeIn(
                            minHeight = PrefCenterTheme.dimens.subscriptionTypeChipMinHeight
                        ),
                        selected = isChecked,
                        onClick = { onCheckedChange(it.scopes, !isChecked) },
                        leadingIcon = { componentsSelectedMark(isChecked) },
                        border = BorderStroke(
                            width = PrefCenterTheme.dimens.subscriptionTypeChipStoke,
                            color = PrefCenterTheme.colors.subscriptionTypeChipStroke
                        ),
                        shape = PrefCenterTheme.shapes.subscriptionTypeChip,
                        colors = FilterChipDefaults.filterChipColors().copy(
                            containerColor = PrefCenterTheme.colors.subscriptionTypeChipSurface,
                            selectedContainerColor = PrefCenterTheme.colors.subscriptionTypeChipSurface,
                        ),
                        label = {
                            it.display.name?.let { text ->
                                Text(
                                    text = text,
                                    color = PrefCenterTheme.colors.subscriptionTypeChipLabelText,
                                    style = PrefCenterTheme.typography.subscriptionTypeChipLabel
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
   Box(
       Modifier
           .size(PrefCenterTheme.dimens.subscriptionTypeChipCheckMarkSize)
           .offset(
               x = PrefCenterTheme.dimens.subscriptionTypeChipCheckMarkOffsetX,
               y = PrefCenterTheme.dimens.subscriptionTypeChipCheckMarkOffsetY
           )
           .clip(CircleShape)
           .background(if (isChecked) {
               PrefCenterTheme.colors.subscriptionTypeChipChecked
           } else {
               PrefCenterTheme.colors.subscriptionTypeChipUnchecked
           })
           .border(
               width = PrefCenterTheme.dimens.subscriptionTypeChipCheckMarkStroke,
               color = PrefCenterTheme.colors.subscriptionTypeChipStroke,
               shape = PrefCenterTheme.shapes.subscriptionTypeChipCheckMark
           )
   ) {
       if (isChecked) {
           Icon(
               modifier = Modifier.padding(PrefCenterTheme.dimens.subscriptionTypeChipCheckMarkPadding),
               painter = painterResource(R.drawable.ua_ic_preference_center_checked),
               tint = PrefCenterTheme.colors.subscriptionTypeChipCheckMark,
               contentDescription = null
           )
       }
   }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            ContactSubscriptionGroupItem(
                item = Item.ContactSubscriptionGroup(
                    id = "id",
                    subscriptionId = "subscriptionId",
                    components = listOf(
                        Item.ContactSubscriptionGroup.Component(
                            scopes = setOf(Scope.APP),
                            display = CommonDisplay("App", "")
                        ),
                        Item.ContactSubscriptionGroup.Component(
                            scopes = setOf(Scope.EMAIL),
                            display = CommonDisplay("Email", "")
                        ),
                        Item.ContactSubscriptionGroup.Component(
                            scopes = setOf(Scope.WEB),
                            display = CommonDisplay("Web", "")
                        ),
                        Item.ContactSubscriptionGroup.Component(
                            scopes = setOf(Scope.SMS),
                            display = CommonDisplay("SMS", "")
                        )
                    ),
                    conditions = emptyList(),
                    display = CommonDisplay(
                        name = "name",
                        description = "description",
                    ),
                )
            ).Content(
                isChecked = { _, scopes -> scopes.contains(Scope.SMS) },
                onCheckedChange = { _, _ -> }
            )
        }
    }
}
