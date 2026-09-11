/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ai

import com.urbanairship.ai.Usage

/**
 * The AI usage for scene text-input inference.
 *
 * Pass it to [com.urbanairship.ai.AirshipAi.setContextProvider] to supply context for text a
 * user types into a scene field:
 *
 * ```
 * Airship.ai.setContextProvider(Usage.sceneTextInput) { subject ->
 *     EvaluationContext(listOf(EvaluationContext.Item("Tier: ${account.tier}")))
 * }
 * ```
 */
public val Usage.Companion.sceneTextInput: Usage<SceneTextInputSubject>
    get() = SCENE_TEXT_INPUT

/**
 * What the app's context provider receives for a text-input evaluation.
 *
 * The renderer puts [text] in the prompt itself and passes [hints] through untouched — neither
 * is a substitute for the context the provider returns.
 *
 * @param text The user's current text for the field being evaluated.
 * @param hints The layout's `subject_hints`, empty when the layout provides none.
 */
public data class SceneTextInputSubject public constructor(
    public val text: String = "",
    public val hints: Map<String, String> = emptyMap()
)

/** Holds the instance, since an extension property can have no backing field of its own. */
private val SCENE_TEXT_INPUT: Usage<SceneTextInputSubject> = Usage("scene_text_input")
