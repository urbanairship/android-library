/* Copyright Airship and Contributors */

package com.urbanairship.actions

import androidx.annotation.RestrictTo
import com.urbanairship.actions.tags.AddTagsAction
import com.urbanairship.actions.tags.ModifyTagsAction
import com.urbanairship.actions.tags.RemoveTagsAction

/**
 * Airship Action Manifest. Defines actions to be loaded into a registry.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ActionsManifest {
    public val manifest: Map<Set<String>, () -> ActionRegistry.Entry>
}


internal class DefaultActionsManifest: ActionsManifest {
    override val manifest: Map<Set<String>, () -> ActionRegistry.Entry> = mapOf(

        // AddCustomEventAction
        AddCustomEventAction.DEFAULT_NAMES to {
            val action = AddCustomEventAction()
            val predicate = AddCustomEventAction.AddCustomEventActionPredicate()
            ActionRegistry.Entry(action = action, predicate = predicate)
        },

        // AddTagsAction
        AddTagsAction.DEFAULT_NAMES to {
            val action = AddTagsAction()
            val predicate = AddTagsAction.AddTagsPredicate()
            ActionRegistry.Entry(action = action, predicate = predicate)
        },

        // ModifyTagsAction
        ModifyTagsAction.DEFAULT_NAMES to {
            val action = ModifyTagsAction()
            val predicate = ModifyTagsAction.ModifyTagsPredicate()
            ActionRegistry.Entry(action = action, predicate = predicate)
        },

        // ClipboardAction
        ClipboardAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = ClipboardAction())
        },

        // DeepLinkAction
        DeepLinkAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = DeepLinkAction())
        },

        // EnableFeatureAction
        EnableFeatureAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = EnableFeatureAction())
        },

        // PromptPermissionAction
        PromptPermissionAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = PromptPermissionAction())
        },

        // FetchDeviceInfoAction
        FetchDeviceInfoAction.DEFAULT_NAMES to {
            val action = FetchDeviceInfoAction()
            val predicate = FetchDeviceInfoAction.FetchDeviceInfoPredicate()
            ActionRegistry.Entry(action = action, predicate = predicate)
        },

        // OpenExternalUrlAction
        OpenExternalUrlAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = OpenExternalUrlAction())
        },

        // RemoveTagsAction
        RemoveTagsAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(
                action = RemoveTagsAction(),
                predicate = RemoveTagsAction.RemoveTagsPredicate()
            )
        },

        // SetAttributesAction
        SetAttributesAction.DEFAULT_NAMES to {
            val action = SetAttributesAction()
            val predicate = SetAttributesAction.SetAttributesPredicate()
            ActionRegistry.Entry(action = action, predicate = predicate)
        },

        // ShareAction
        ShareAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = ShareAction())
        },

        // ToastAction
        ToastAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = ToastAction())
        },

        // RateAppAction
        RateAppAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = RateAppAction())
        },

        // WalletAction
        WalletAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = WalletAction())
        },

        // SubscriptionListAction
        SubscriptionListAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(action = SubscriptionListAction())
        }
    )
}
