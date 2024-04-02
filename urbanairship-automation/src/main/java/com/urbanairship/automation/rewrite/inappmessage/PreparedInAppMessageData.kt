package com.urbanairship.automation.rewrite.inappmessage

import com.urbanairship.automation.rewrite.engine.AutomationPreparerDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorInterface

internal data class PreparedInAppMessageData(
    val message: InAppMessage,
    val displayAdapter: DisplayAdapterInterface,
    val displayCoordinator: DisplayCoordinatorInterface
)

//TODO: Implement InAppMessageAutomationPreparer
internal sealed class InAppMessageAutomationPreparer(

) : AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData> {

}
