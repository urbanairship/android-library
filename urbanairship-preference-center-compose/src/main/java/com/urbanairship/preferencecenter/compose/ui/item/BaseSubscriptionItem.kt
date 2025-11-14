package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.core.R

@Composable
internal fun BaseSubscriptionItem(
    title: String?,
    subtitle: String?,
    isChecked: () -> Boolean,
    onCheckedChanged: (checked: Boolean) -> Unit,
    accessibilityDescription: () -> String,
    modifier: Modifier = Modifier
) {
    val accessibilityStateDescription = stringResource(if (isChecked()) {
        R.string.ua_preference_center_subscribed_description
    } else {
        R.string.ua_preference_center_unsubscribed_description
    })

    val accessibilityAction = CustomAccessibilityAction(
        label = stringResource(if (isChecked()) {
            R.string.ua_preference_center_action_unsubscribe
        } else {
            R.string.ua_preference_center_action_subscribe
        }),
        action = {
            onCheckedChanged(!isChecked())
            true
        }
    )

    Surface(
        color = PrefCenterTheme.colors.surface,
        modifier = modifier.focusGroup()
            .semantics {
                contentDescription = accessibilityDescription()
                customActions = listOf(accessibilityAction)
            }
            .toggleable(value = isChecked(), onValueChange = { onCheckedChanged(!isChecked()) } )
            .clearAndSetSemantics {
                stateDescription = accessibilityStateDescription
                toggleableState = ToggleableState(isChecked())
                role = Role.Switch
            }
    ) {
        Row(Modifier.fillMaxWidth().padding(PrefCenterTheme.dimens.itemPadding)) {
            Column(Modifier.weight(1f)) {
                title?.let { text ->
                    Text(
                        text = text,
                        style = PrefCenterTheme.typography.itemTitle,
                        color = PrefCenterTheme.colors.itemTitleText,
                        modifier = Modifier.padding(PrefCenterTheme.dimens.itemTitlePadding)
                    )
                }

                subtitle?.let { text ->
                    Text(
                        text = text,
                        style = PrefCenterTheme.typography.itemDescription,
                        color = PrefCenterTheme.colors.itemDescriptionText,
                        modifier = Modifier.padding(PrefCenterTheme.dimens.itemDescriptionPadding)
                    )
                }
            }

            Switch(
                checked = isChecked(),
                onCheckedChange = onCheckedChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrefCenterTheme.colors.subscriptionSwitchCheckedThumbColor,
                    checkedTrackColor = PrefCenterTheme.colors.subscriptionSwitchCheckedTrackColor,
                    uncheckedThumbColor = PrefCenterTheme.colors.subscriptionSwitchUncheckedThumbColor,
                    uncheckedTrackColor = PrefCenterTheme.colors.subscriptionSwitchUncheckedTrackColor,
                ),
                thumbContent = {
                    if (PrefCenterTheme.options.showSwitchIcons) {
                        Crossfade(targetState = isChecked()) { state ->
                            Icon(
                                painter =  if (state) painterResource(R.drawable.ua_ic_preference_center_checked)
                                else painterResource(R.drawable.ua_ic_preference_center_unchecked),
                                contentDescription = null,
                                tint = PrefCenterTheme.colors.surface,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.sizeIn(minWidth = PrefCenterTheme.dimens.subscriptionSwitchMinWidth)
                    .padding(PrefCenterTheme.dimens.subscriptionSwitchPadding)
            )
        }
    }
}
