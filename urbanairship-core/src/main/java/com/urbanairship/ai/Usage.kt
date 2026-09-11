/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.annotation.RestrictTo

/**
 * Identifies an AI use case — one key per evaluable feature.
 *
 * Apps don't declare these. Each SDK feature that runs evaluations exposes its own typed
 * constant, which is what you pass to [AirshipAi.setContextProvider] and compare against in a
 * [ModelResolver].
 *
 * [Subject] is the feature-specific data a provider receives, so registering a provider for the
 * wrong usage is a compile error.
 *
 * [rawValue] alone is the identity: providers are keyed by it, and because the SDK owns every
 * constant there is exactly one [Subject] per key. That is also what lets a `Usage<*>` — the
 * erased form handed to a [ModelResolver] or carried on an [EvaluationRecord] — be compared
 * against a feature's typed constant with `==`.
 *
 * @param rawValue The usage key.
 */
public class Usage<Subject> @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) constructor(
    public val rawValue: String
) {
    override fun equals(other: Any?): Boolean = other is Usage<*> && rawValue == other.rawValue
    override fun hashCode(): Int = rawValue.hashCode()
    override fun toString(): String = rawValue

    /**
     * Where every feature's usage is reachable from, whichever module declares it.
     *
     * A feature module extends this with the usage it owns, so an app finds them all by
     * completing on `Usage.` rather than having to know which module to import:
     *
     * ```
     * public val Usage.Companion.sceneTextInput: Usage<SceneTextInputSubject>
     *     get() = SCENE_TEXT_INPUT
     * ```
     */
    public companion object
}
