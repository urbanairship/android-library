/* Copyright Airship and Contributors */
package com.urbanairship.iam.ai

import com.urbanairship.ai.Usage
import com.urbanairship.json.JsonValue

/**
 * The AI usage for in-app message suppression.
 *
 * Pass it to [com.urbanairship.ai.AirshipAi.setContextProvider] to supply the context a model
 * weighs before a message is shown:
 *
 * ```
 * Airship.ai.setContextProvider(Usage.inAppMessageSuppression) { subject ->
 *     EvaluationContext(listOf(EvaluationContext.Item("Last booked: ${store.lastBooking}")))
 * }
 * ```
 */
public val Usage.Companion.inAppMessageSuppression: Usage<InAppMessageSuppressionSubject>
    get() = IN_APP_MESSAGE_SUPPRESSION

/**
 * What the app's context provider receives for a suppression evaluation, so it can answer with
 * context about the specific message being considered.
 *
 * @param name The message name.
 * @param extras The message's extras.
 * @param priority The schedule priority, where lower runs first.
 * @param hints The schedule's `subject_hints`. Handed to the provider only — never rendered
 * into the prompt.
 */
public data class InAppMessageSuppressionSubject public constructor(
    public val name: String,
    public val extras: JsonValue? = null,
    public val priority: Int = 0,
    public val hints: Map<String, String> = emptyMap()
)

/** Holds the instance, since an extension property can have no backing field of its own. */
private val IN_APP_MESSAGE_SUPPRESSION: Usage<InAppMessageSuppressionSubject> =
    Usage("in_app_message_suppression")
