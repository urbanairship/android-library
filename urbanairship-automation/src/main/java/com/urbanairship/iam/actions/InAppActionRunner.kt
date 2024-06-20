package com.urbanairship.iam.actions

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionCompletionCallback
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestExtender
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.AddCustomEventAction
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppPermissionResultEvent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

internal class InAppActionRunner(
    private val analytics: InAppMessageAnalyticsInterface,
    private val trackPermissionResults: Boolean,
    private val actionRequestFactory: (String) -> ActionRunRequest = { ActionRunRequest.createRequest(it) }
): ActionRunner, ThomasActionRunner {

    override fun run(
        name: String,
        value: JsonSerializable?,
        situation: Int?,
        extender: ActionRunRequestExtender?,
        callback: ActionCompletionCallback?
    ) {
        run(name, value, situation, extender, callback, metadata(null))
    }

    override fun run(actions: Map<String, JsonValue>, state: LayoutData) {
        val metadata = metadata(state)
        actions.forEach {
            run(it.key, it.value, Action.SITUATION_AUTOMATION, null, null, metadata)
        }
    }

    private fun run(
        name: String,
        value: JsonSerializable?,
        situation: Int?,
        extender: ActionRunRequestExtender?,
        callback: ActionCompletionCallback?,
        metadata: Bundle
    ) {
        actionRequestFactory(name)
            .setValue(value)
            .setMetadata(metadata)
            .let { request ->
                situation?.let {
                    request.setSituation(situation)
                }

                extender?.let {
                    extender.extend(request)
                } ?: request
            }
            .run(callback)
    }

    private fun metadata(state: LayoutData?): Bundle {
        val bundle = Bundle()

        if (trackPermissionResults) {
            val receiver = object : PermissionResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onResult(
                    permission: Permission,
                    before: PermissionStatus,
                    after: PermissionStatus
                ) {
                    analytics.recordEvent(
                        event = InAppPermissionResultEvent(permission, before, after),
                        layoutContext = state
                    )
                }
            }

            bundle.putParcelable(
                PromptPermissionAction.RECEIVER_METADATA,
                receiver
            )
        }

        bundle.putString(
            AddCustomEventAction.IN_APP_CONTEXT_METADATA_KEY,
            analytics.customEventContext(state).toJsonValue().toString()
        )

        return bundle
    }
}
