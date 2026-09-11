/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ai

import com.urbanairship.ai.Usage

/**
 * The AI usage for scene text-input inference.
 *
 * Pass [usage] to [com.urbanairship.ai.AirshipAi.setContextProvider] to supply context for text
 * a user types into a scene field:
 *
 * ```
 * Airship.ai.setContextProvider(SceneTextInputInference.usage) { subject ->
 *     EvaluationContext(listOf(EvaluationContext.Item("Tier: ${account.tier}")))
 * }
 * ```
 */
public object SceneTextInputInference {

    /** The usage key for scene text-input inference. */
    public val usage: Usage<Subject> = Usage("scene_text_input")

    /**
     * What the app's context provider receives for a text-input evaluation.
     *
     * The renderer puts [text] in the prompt itself and passes [hints] through untouched —
     * neither is a substitute for the context the provider returns.
     *
     * @param text The user's current text for the field being evaluated.
     * @param hints The layout's `subject_hints`, empty when the layout provides none.
     */
    public data class Subject public constructor(
        public val text: String = "",
        public val hints: Map<String, String> = emptyMap()
    )
}
