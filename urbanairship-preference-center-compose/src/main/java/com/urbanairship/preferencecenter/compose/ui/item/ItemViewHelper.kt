package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.runtime.Composable
import com.urbanairship.preferencecenter.compose.ui.Action
import com.urbanairship.preferencecenter.compose.ui.PreferenceCenterViewModel
import com.urbanairship.preferencecenter.compose.ui.ViewState

internal object ItemViewHelper {

    @Composable
    fun createItemView(
        item: BasePrefCenterItem,
        viewState: ViewState.Content,
        model: PreferenceCenterViewModel
    ) {
        return when (item) {
            is SectionItem -> item.toView()
            is SectionBreakItem -> item.toView()
            is DescriptionItem -> item.toView()
            is ContactSubscriptionItem -> item.toView(
                isChecked = { id, scope ->
                    viewState.contactSubscriptions[id]?.containsAll(scope) ?: false
                },
                onCheckedChanged = { checked ->
                    model.handle(
                        Action.ScopedPreferenceItemChanged(
                            item = item.item,
                            scopes = item.scopes,
                            isEnabled = checked)
                    )
                }
            )
            is ContactSubscriptionGroupItem -> item.toView(
                isChecked = { id, scope ->
                    viewState.contactSubscriptions[id]?.containsAll(scope) ?: false
                },
                onCheckedChange = { scopes, checked ->
                    model.handle(
                        Action.ScopedPreferenceItemChanged(
                            item = item.item,
                            scopes = scopes,
                            isEnabled = checked)
                    )
                }
            )
            is ContactManagementItem -> item.toView(
                contactChannelsProvider = { viewState.contactChannelState },
                handler = { action ->
                    val modelAction = when(action) {
                        ContactManagementItem.Action.Add -> Action.RequestAddChannel(item.item)
                        is ContactManagementItem.Action.Remove -> Action.RequestRemoveChannel(item.item, action.channel)
                        is ContactManagementItem.Action.Resend -> Action.ResendChannelVerification(item.item, action.channel)
                    }

                    model.handle(modelAction)
                }
            )
            is ChannelSubscriptionItem -> item.toView(
                isChecked = { viewState.channelSubscriptions.contains(item.subscriptionId) },
                onCheckedChanged = { checked -> model.handle(Action.PreferenceItemChanged(item.item, checked)) }
            )
            is AlertItem -> item.toView {
                model.handle(Action.ButtonActions(it))
            }
        }
    }
}
