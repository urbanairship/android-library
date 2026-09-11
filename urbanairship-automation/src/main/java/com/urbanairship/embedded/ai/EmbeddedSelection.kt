/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import com.urbanairship.ai.Usage
import com.urbanairship.embedded.AirshipEmbeddedInfo

/**
 * The AI usage for embedded-view selection.
 *
 * Pass it to [com.urbanairship.ai.AirshipAi.setContextProvider] to supply the user context a
 * model weighs when choosing between pending embedded content:
 *
 * ```
 * Airship.ai.setContextProvider(Usage.embeddedSelection) { subject ->
 *     EvaluationContext(listOf(EvaluationContext.Item("Interests: ${profile.interests}")))
 * }
 * ```
 *
 * Its own usage, independent of scene text-input inference. Declared in this module but hung
 * off [Usage.Companion], so every usage the SDK has is reachable from one place whichever
 * module owns it.
 */
public val Usage.Companion.embeddedSelection: Usage<EmbeddedSelectionSubject>
    get() = EMBEDDED_SELECTION

/**
 * What the app's context provider receives for an embedded-selection evaluation.
 *
 * @param embeddedId The embedded ID being selected for.
 * @param pending The candidates being ranked — the set the model is choosing between. A
 * provider can inspect these to build context relevant to the actual choice.
 * @param hints The layout's `subject_hints`, empty when the layout provides none.
 */
public data class EmbeddedSelectionSubject public constructor(
    public val embeddedId: String = "",
    public val pending: List<AirshipEmbeddedInfo> = emptyList(),
    public val hints: Map<String, String> = emptyMap()
)

/** Holds the instance, since an extension property can have no backing field of its own. */
private val EMBEDDED_SELECTION: Usage<EmbeddedSelectionSubject> = Usage("embedded_selection")
