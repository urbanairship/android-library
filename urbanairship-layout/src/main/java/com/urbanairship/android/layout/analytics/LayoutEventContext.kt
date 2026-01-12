/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class LayoutEventContext(
    val pager: Pager? = null,
    val button: Button? = null,
    val form: Form? = null,
    val display: Display? = null,
    val reportingContext: JsonValue? = null,
    val experimentReportingData: List<JsonMap>? = null
) : JsonSerializable {
    public data class Pager(
        var identifier: String,
        var pageIdentifier: String,
        var pageIndex: Int,
        var completed: Boolean,
        var history: List<JsonSerializable>,
        var count: Int
    ) : JsonSerializable {
        private companion object {
            private const val KEY_IDENTIFIER = "identifier"
            private const val KEY_PAGE_IDENTIFIER = "page_identifier"
            private const val KEY_PAGE_INDEX = "page_index"
            private const val KEY_COMPLETED = "completed"
            private const val KEY_HISTORY = "page_history"
            private const val KEY_COUNT = "count"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_IDENTIFIER to identifier,
            KEY_PAGE_IDENTIFIER to pageIdentifier,
            KEY_PAGE_INDEX to pageIndex,
            KEY_COMPLETED to completed,
            KEY_HISTORY to history,
            KEY_COUNT to count
        ).toJsonValue()
    }

    public data class Form(
        val identifier: String,
        val submitted: Boolean,
        val type: String,
        val responseType: String? = null
    ) : JsonSerializable {
        private companion object {
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

    public data class Button(
        val identifier: String
    ) : JsonSerializable {
        private companion object {
            private const val KEY_IDENTIFIER = "identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_IDENTIFIER to identifier
        ).toJsonValue()
    }

    public companion object {
        private const val KEY_PAGER = "pager"
        private const val KEY_BUTTON = "button"
        private const val KEY_FORM = "form"
        private const val KEY_DISPLAY = "display"
        private const val KEY_REPORTING_CONTEXT = "reporting_context"
        private const val KEY_EXPERIMENTS_REPORTING_DATA = "experiments"
    }

    public data class Display(
        val triggerSessionId: String,
        var isFirstDisplay: Boolean,
        var isFirstDisplayTriggerSessionId: Boolean
    ) : JsonSerializable {
        private companion object {
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


public fun LayoutEventContext.Companion.makeContext(
    reportingContext: JsonValue?,
    experimentResult: ExperimentResult?,
    layoutContext: LayoutData?,
    displayContext: LayoutEventContext.Display?
) : LayoutEventContext? {
    val result = LayoutEventContext(
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

private fun LayoutEventContext.Companion.makeButtonContext(context: LayoutData?): LayoutEventContext.Button? {
    val identifier = context?.buttonIdentifier ?: return null
    return LayoutEventContext.Button(identifier)
}

private fun LayoutEventContext.Companion.makeFormContext(context: LayoutData?): LayoutEventContext.Form? {
    val info = context?.formInfo ?: return null
    return LayoutEventContext.Form(
        identifier = info.identifier,
        submitted = info.isFormSubmitted ?: false,
        type = info.formType,
        responseType = info.formResponseType
    )
}

private fun LayoutEventContext.Companion.makePagerContext(context: LayoutData?): LayoutEventContext.Pager? {
    val info = context?.pagerData ?: return null
    return LayoutEventContext.Pager(
        identifier = info.identifier,
        pageIdentifier = info.pageId,
        pageIndex = info.index,
        completed = info.isCompleted,
        count = info.count,
        history = info.history
    )
}
