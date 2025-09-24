package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.runtime.Composable
import com.urbanairship.preferencecenter.compose.ui.Action
import com.urbanairship.preferencecenter.compose.ui.ViewState

internal object ItemViewHelper {

    @Composable
    fun createItemView(
        item: BasePrefCenterItem,
        viewState: ViewState.Content,
        onAction: (Action) -> Unit,
    ) {
        return when (item) {
            is SectionItem -> item.Content()
            is SectionBreakItem -> item.Content()
            is DescriptionItem -> item.Content()
            is ContactSubscriptionItem -> item.Content(
                isChecked = { id, scope ->
                    viewState.contactSubscriptions[id]?.containsAll(scope) ?: false
                },
                onCheckedChanged = { checked ->
                    onAction(
                        Action.ScopedPreferenceItemChanged(
                            item = item.item,
                            scopes = item.scopes,
                            isEnabled = checked
                        )
                    )
                }
            )
            is ContactSubscriptionGroupItem -> item.Content(
                isChecked = { id, scope ->
                    viewState.contactSubscriptions[id]?.containsAll(scope) ?: false
                },
                onCheckedChange = { scopes, checked ->
                    onAction(
                        Action.ScopedPreferenceItemChanged(
                            item = item.item,
                            scopes = scopes,
                            isEnabled = checked
                        )
                    )
                }
            )
            is ContactManagementItem -> item.Content(
                contactChannelsProvider = { viewState.contactChannelState },
                handler = { action ->
                    val modelAction = when(action) {
                        ContactManagementItem.Action.Add -> Action.RequestAddChannel(item.item)
                        is ContactManagementItem.Action.Remove -> Action.RequestRemoveChannel(item.item, action.channel)
                        is ContactManagementItem.Action.Resend -> Action.ResendChannelVerification(item.item, action.channel)
                    }

                    onAction(modelAction)
                }
            )
            is ChannelSubscriptionItem -> item.Content(
                isChecked = { viewState.channelSubscriptions.contains(item.subscriptionId) },
                onCheckedChanged = { checked -> onAction(Action.PreferenceItemChanged(item.item, checked)) }
            )
            is AlertItem -> item.Content {
                onAction(Action.ButtonActions(it))
            }
        }
    }
}
