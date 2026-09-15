/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ai

import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * What a text input's inference produced, in the two shapes Thomas needs it: the layout-state
 * projection predicates and pager branching read, and the payload the form event reports.
 *
 * The two differ on purpose. [stateProjection] carries the model's whole output, which never
 * leaves the device. [reported] carries only the values the schema opted into reporting.
 */
internal sealed class ThomasAIInferenceOutcome {

    /** The `$forms` projection: the model's full output. */
    abstract val stateProjection: JsonValue

    /** The `ai_inference` payload on the form event. */
    abstract val reported: JsonValue

    /**
     * A completed inference.
     *
     * @param output The model's output.
     * @param schema The schema it was produced against, which decides what may be reported.
     */
    data class Complete(
        val output: JsonValue,
        private val schema: AirshipJsonSchema
    ) : ThomasAIInferenceOutcome() {

        override val stateProjection: JsonValue
            get() = jsonMapOf(STATUS to STATUS_COMPLETE, RESULT to output).toJsonValue()

        override val reported: JsonValue
            get() = jsonMapOf(
                RESULT to RESULT_SUCCESS,
                OUTPUT to reportedOutput(output, schema)
            ).toJsonValue()
    }

    /** A failed or unavailable inference. */
    data object Failed : ThomasAIInferenceOutcome() {

        override val stateProjection: JsonValue
            get() = jsonMapOf(STATUS to STATUS_FAILED).toJsonValue()

        override val reported: JsonValue
            get() = jsonMapOf(RESULT to RESULT_FAILED).toJsonValue()
    }

    internal companion object {
        private const val STATUS = "status"
        private const val RESULT = "result"
        private const val OUTPUT = "output"
        private const val STATUS_COMPLETE = "complete"
        private const val STATUS_FAILED = "failed"
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_FAILED = "failed"

        /** Schema extension keyword opting a property into reporting. */
        private const val REPORT_PROPERTY_KEY = "x-ua-report-property"

        /**
         * Keeps only the values reachable through an unbroken chain of nodes flagged
         * `x-ua-report-property`. An unflagged node prunes itself and its whole subtree, so an
         * unflagged object omits even its flagged children and an unflagged root reports
         * nothing.
         *
         * @param value The value to filter.
         * @param schema The schema node for [value].
         * @return The filtered value, or `null` when nothing under [schema] is reportable.
         */
        private fun reportedOutput(value: JsonValue, schema: AirshipJsonSchema): JsonValue? {
            if (!schema.isReportProperty) {
                return null
            }

            return when (val type = schema.type) {
                is AirshipJsonSchema.ValueType.ObjectType -> {
                    val properties = type.properties ?: return null
                    val map = value.map ?: return null
                    val kept = properties.mapNotNull { (name, propertySchema) ->
                        val child = map[name] ?: return@mapNotNull null
                        reportedOutput(child, propertySchema)?.let { name to it }
                    }
                    if (kept.isEmpty()) null else JsonMap(kept.toMap()).toJsonValue()
                }
                is AirshipJsonSchema.ValueType.ArrayType -> {
                    val list = value.list ?: return null
                    val kept = list.mapNotNull { reportedOutput(it, type.items) }
                    if (kept.isEmpty()) null else JsonValue.wrap(kept)
                }
                AirshipJsonSchema.ValueType.BooleanType,
                AirshipJsonSchema.ValueType.IntegerType,
                AirshipJsonSchema.ValueType.NumberType,
                is AirshipJsonSchema.ValueType.StringType -> value
            }
        }

        private val AirshipJsonSchema.isReportProperty: Boolean
            get() = extensions[REPORT_PROPERTY_KEY]?.getBoolean(false) ?: false
    }
}
