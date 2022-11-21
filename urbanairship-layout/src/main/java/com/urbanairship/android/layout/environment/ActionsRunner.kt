package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.property.Actions
import com.urbanairship.android.layout.reporting.LayoutData

internal interface ActionsRunner {
    fun run(actions: Actions, state: LayoutData)
}

internal class ExternalActionsRunner(val listener: ThomasListener) : ActionsRunner {
    override fun run(actions: Actions, state: LayoutData) {
        listener.onRunActions(actions, state)
    }
}
