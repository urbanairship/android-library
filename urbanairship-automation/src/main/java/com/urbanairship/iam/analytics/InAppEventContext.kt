/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal data class InAppEventContext(
    val pager: Pager? = null,
    val button: Button? = null,
    val form: Form? = null,
    val display: Display? = null,
    val reportingContext: JsonValue? = null,
    val experimentReportingData: List<JsonMap>? = null
) : JsonSerializable {
    data class Pager(
        var identifier: String,
        var pageIdentifier: String,
        var pageIndex: Int,
        var completed: Boolean,
        var count: Int
    ) : JsonSerializable {
        companion object {
            private const val KEY_IDENTIFIER = "identifier"
            private const val KEY_PAGE_IDENTIFIER = "page_identifier"
            private const val KEY_PAGE_INDEX = "page_index"
            private const val KEY_COMPLETED = "completed"
            private const val KEY_COUNT = "count"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_IDENTIFIER to identifier,
            KEY_PAGE_IDENTIFIER to pageIdentifier,
            KEY_PAGE_INDEX to pageIndex,
            KEY_COMPLETED to completed,
            KEY_COUNT to count
        ).toJsonValue()
    }

    data class Form(
        val identifier: String,
        val submitted: Boolean,
        val type: String,
        val responseType: String? = null
    ) : JsonSerializable {
        companion object {
            private const val KEY_IDENTIFIER = "identifier"
            private const val KEY_SUBMITTED = "submitted"
            private const val KEY_TYPE = "type"
            private const val KEY_RESPONSE_TYPE = "response_type"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_IDENTIFIER to identifier,
            KEY_SUBMITTED to submitted,
            KEY_TYPE to type,
            KEY_RESPONSE_TYPE to responseType
        ).toJsonValue()
    }

    data class Button(
        val identifier: String
    ) : JsonSerializable {
        companion object {
            private const val KEY_IDENTIFIER = "identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_IDENTIFIER to identifier
        ).toJsonValue()
    }

    companion object {
        private const val KEY_PAGER = "pager"
        private const val KEY_BUTTON = "button"
        private const val KEY_FORM = "form"
        private const val KEY_DISPLAY = "display"
        private const val KEY_REPORTING_CONTEXT = "reporting_context"
        private const val KEY_EXPERIMENTS_REPORTING_DATA = "experiments"
    }

    data class Display(
        val triggerSessionId: String,
        var isFirstDisplay: Boolean,
        var isFirstDisplayTriggerSessionId: Boolean
    ) : JsonSerializable {
        companion object {
            private const val KEY_TRIGGER_SESSION_ID = "trigger_session_id"
            private const val KEY_IS_FIRST_DISPLAY = "is_first_display"
            private const val KEY_IS_FIRST_DISPLAY_TRIGGER_SESSION_ID = "is_first_display_trigger_session"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_TRIGGER_SESSION_ID to triggerSessionId,
            KEY_IS_FIRST_DISPLAY to isFirstDisplay,
            KEY_IS_FIRST_DISPLAY_TRIGGER_SESSION_ID to isFirstDisplayTriggerSessionId
        ).toJsonValue()
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_PAGER to pager,
        KEY_BUTTON to button,
        KEY_FORM to form,
        KEY_DISPLAY to display,
        KEY_REPORTING_CONTEXT to reportingContext,
        KEY_EXPERIMENTS_REPORTING_DATA to experimentReportingData
    ).toJsonValue()

    internal fun isValid(): Boolean {
        return pager != null || button != null || form != null || display != null || reportingContext != null ||
                (experimentReportingData?.isNotEmpty() ?: false)
    }
}

internal fun InAppEventContext.Companion.makeContext(
    reportingContext: JsonValue?,
    experimentResult: ExperimentResult?,
    layoutContext: LayoutData?,
    displayContext: InAppEventContext.Display?
) : InAppEventContext? {
    val result = InAppEventContext(
        pager = makePagerContext(layoutContext),
        button = makeButtonContext(layoutContext),
        form = makeFormContext(layoutContext),
        display = displayContext,
        reportingContext = reportingContext,
        experimentReportingData = experimentResult?.allEvaluatedExperimentsMetadata
    )

    if (!result.isValid()) {
        return null
    }

    return result
}

private fun InAppEventContext.Companion.makeButtonContext(context: LayoutData?): InAppEventContext.Button? {
    val identifier = context?.buttonIdentifier ?: return null
    return InAppEventContext.Button(identifier)
}

private fun InAppEventContext.Companion.makeFormContext(context: LayoutData?): InAppEventContext.Form? {
    val info = context?.formInfo ?: return null
    return InAppEventContext.Form(
        identifier = info.identifier,
        submitted = info.formSubmitted ?: false,
        type = info.formType,
        responseType = info.formResponseType
    )
}

private fun InAppEventContext.Companion.makePagerContext(context: LayoutData?): InAppEventContext.Pager? {
    val info = context?.pagerData ?: return null
    return InAppEventContext.Pager(
        identifier = info.identifier,
        pageIdentifier = info.pageId,
        pageIndex = info.index,
        completed = info.isCompleted,
        count = info.count
    )
}
