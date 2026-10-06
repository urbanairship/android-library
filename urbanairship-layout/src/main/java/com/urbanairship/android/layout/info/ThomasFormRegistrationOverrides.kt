/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/**
 * Registration overrides for the inputs of one form, applied on form submission.
 *
 * Predicates and state values are read from the form's state at submit, never while the user is
 * filling the form in. Every matching override applies, in list order, on top of the input's own
 * `email_registration`, and an input with none registers once a patch supplies a `type`.
 *
 * @param email The email overrides, in the order they apply.
 */
internal data class ThomasFormRegistrationOverrides(
    val email: List<ThomasEmailRegistrationOverride>?
) {

    /**
     * Returns the email registration for an input after applying every matching override.
     *
     * For example, an input with `transactional` options and a matching override of
     * `{type: commercial, commercial_opted_in: {type: value, value: true}}` resolves to
     * [ThomasEmailRegistrationOptions.Commercial] opted in, keeping the input's properties.
     *
     * @param inputId The email input's identifier.
     * @param options The input's own `email_registration`.
     * @param state The form's state, the root both predicates and `state` patch values read from.
     * @return The resolved options, or `null` when neither the input nor a patch supplies a type.
     */
    fun resolveEmail(
        inputId: String,
        options: ThomasEmailRegistrationOptions?,
        state: JsonValue
    ): ThomasEmailRegistrationOptions? {
        val draft = EmailRegistrationDraft(options)
        email
            ?.filter { it.inputId == null || it.inputId == inputId }
            ?.filter { it.whenStateMatches.apply(state) }
            ?.forEach { draft.apply(it.registration, state) }
        return draft.options()
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): ThomasFormRegistrationOverrides {
            val content = json.requireMap()
            return ThomasFormRegistrationOverrides(
                email = content.optionalList("email")?.map(ThomasEmailRegistrationOverride::fromJson)
            )
        }
    }
}

/**
 * An email patch, applied when [whenStateMatches] matches the form's state.
 *
 * @param inputId The email input to patch; `null` patches every email input in the form.
 * @param whenStateMatches The predicate evaluated against the form's state at submit.
 * @param registration The patch.
 */
internal data class ThomasEmailRegistrationOverride(
    val inputId: String?,
    val whenStateMatches: JsonPredicate,
    val registration: ThomasEmailRegistrationPatch
) {
    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): ThomasEmailRegistrationOverride {
            val content = json.requireMap()
            return ThomasEmailRegistrationOverride(
                inputId = content.optionalField("input_id"),
                whenStateMatches = JsonPredicate.parse(content.requireField<JsonValue>("when_state_matches")),
                registration = ThomasEmailRegistrationPatch.fromJson(content.requireField("registration"))
            )
        }
    }
}

/**
 * The fields of an email registration an override changes; `null` fields keep the input's value.
 *
 * @param type The registration type.
 * @param commercialOptedIn Whether the address is opted in to commercial email.
 * @param openTrackingOptedIn Whether the address is opted in to open tracking.
 * @param clickTrackingOptedIn Whether the address is opted in to click tracking.
 * @param properties Changes to the registration's properties.
 */
internal data class ThomasEmailRegistrationPatch(
    val type: ThomasEmailRegistrationOptions.Type?,
    val commercialOptedIn: ThomasPatchValue?,
    val openTrackingOptedIn: ThomasPatchValue?,
    val clickTrackingOptedIn: ThomasPatchValue?,
    val properties: PropertiesPatch?
) {

    /**
     * Changes to `properties`, one top-level key at a time. [remove] runs before [set], so a key
     * listed in both ends up set.
     *
     * @param set Keys to add or overwrite.
     * @param remove Keys to remove; one that isn't there is ignored.
     */
    data class PropertiesPatch(
        val set: Map<String, ThomasPatchValue>?,
        val remove: List<String>?
    ) {
        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): PropertiesPatch {
                val content = json.requireMap()
                return PropertiesPatch(
                    set = content.optionalMap("set")?.map?.mapValues { ThomasPatchValue.fromJson(it.value) },
                    remove = content.optionalList("remove")?.map { it.requireString() }
                )
            }
        }
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): ThomasEmailRegistrationPatch {
            val content = json.requireMap()
            return ThomasEmailRegistrationPatch(
                type = content.optionalField<String>("type")?.let(ThomasEmailRegistrationOptions::parseType),
                commercialOptedIn = content["commercial_opted_in"]?.let(ThomasPatchValue::fromJson),
                openTrackingOptedIn = content["open_tracking_opted_in"]?.let(ThomasPatchValue::fromJson),
                clickTrackingOptedIn = content["click_tracking_opted_in"]?.let(ThomasPatchValue::fromJson),
                properties = content["properties"]?.let(PropertiesPatch::fromJson)
            )
        }
    }
}

/** A patch value, either written into the layout or read from the form's state at submit. */
internal sealed class ThomasPatchValue {

    data class Literal(val value: JsonValue) : ThomasPatchValue()

    data class State(val scope: List<String>) : ThomasPatchValue()

    /**
     * Returns the patch's value, or `null` when a [State] scope finds nothing (missing or `null`).
     *
     * @param state The form's state.
     * @return The resolved value, or `null` to leave the field as it was.
     */
    fun resolve(state: JsonValue): JsonValue? {
        val value = when (this) {
            is Literal -> value
            is State -> scope.fold(state) { json, key -> json.optMap().opt(key) }
        }
        return value.takeUnless { it.isNull }
    }

    /**
     * Returns the patch's value as a boolean, or `null` when it resolves to nothing or to another
     * type; nothing is coerced.
     *
     * @param state The form's state.
     * @return The resolved boolean, or `null` to leave the field as it was.
     */
    fun resolveBoolean(state: JsonValue): Boolean? =
        resolve(state)?.takeIf { it.isBoolean }?.getBoolean(false)

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): ThomasPatchValue {
            val content = json.requireMap()
            return when (val type = content.requireField<String>("type")) {
                "value" -> Literal(content.requireField("value"))
                "state" -> State(content.requireField<JsonValue>("scope").requireList().map { it.requireString() })
                else -> throw JsonException("Invalid patch value type: $type")
            }
        }
    }
}

/**
 * The flattened fields of an email registration while patches apply, so a value set for one type
 * survives a patch that switches to another and back.
 */
private class EmailRegistrationDraft(options: ThomasEmailRegistrationOptions?) {
    private var type: ThomasEmailRegistrationOptions.Type? = options?.type
    private var commercialOptedIn: Boolean? =
        (options as? ThomasEmailRegistrationOptions.Commercial)?.optedIn
    private var openTrackingOptedIn: Boolean? = options?.openTrackingOptedIn
    private var clickTrackingOptedIn: Boolean? = options?.clickTrackingOptedIn
    private var properties: JsonMap? = options?.properties

    fun apply(patch: ThomasEmailRegistrationPatch, state: JsonValue) {
        patch.type?.let { type = it }
        patch.commercialOptedIn?.resolveBoolean(state)?.let { commercialOptedIn = it }
        patch.openTrackingOptedIn?.resolveBoolean(state)?.let { openTrackingOptedIn = it }
        patch.clickTrackingOptedIn?.resolveBoolean(state)?.let { clickTrackingOptedIn = it }

        patch.properties?.let { propertiesPatch ->
            val updated = properties?.map?.toMutableMap() ?: mutableMapOf()
            propertiesPatch.remove?.forEach { updated.remove(it) }
            propertiesPatch.set?.forEach { (key, value) ->
                value.resolve(state)?.let { updated[key] = it }
            }
            properties = JsonMap(updated)
        }
    }

    fun options(): ThomasEmailRegistrationOptions? = when (type) {
        ThomasEmailRegistrationOptions.Type.DOUBLE_OPT_IN ->
            ThomasEmailRegistrationOptions.DoubleOptIn(properties, openTrackingOptedIn, clickTrackingOptedIn)
        ThomasEmailRegistrationOptions.Type.COMMERCIAL ->
            ThomasEmailRegistrationOptions.Commercial(
                properties = properties,
                optedIn = commercialOptedIn ?: false,
                openTrackingOptedIn = openTrackingOptedIn,
                clickTrackingOptedIn = clickTrackingOptedIn
            )
        ThomasEmailRegistrationOptions.Type.TRANSACTIONAL ->
            ThomasEmailRegistrationOptions.Transactional(properties, openTrackingOptedIn, clickTrackingOptedIn)
        null -> null
    }
}
