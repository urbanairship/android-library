/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.RestrictTo

/**
 * Controls which pending embedded content instance is displayed in an `AirshipEmbeddedView`.
 */
public sealed class AirshipEmbeddedSelection {

    /**
     * Display content using priority ordering, with sticky last-displayed behavior.
     *
     * This is the default selection mode. The instance with the lowest numeric priority value
     * is displayed. Once an instance is selected, it remains displayed until dismissed, even
     * if a higher-priority instance arrives, preventing unnecessary thrashing.
     */
    public data object Priority : AirshipEmbeddedSelection()

    /**
     * Display content using the provided [comparator] to sort available embedded instances.
     *
     * The instance that sorts first is displayed. Sticky last-displayed behavior is bypassed.
     *
     * @param comparator the [Comparator] used to sort available embedded instances.
     */
    public data class ByComparator(
        public val comparator: Comparator<AirshipEmbeddedInfo>
    ) : AirshipEmbeddedSelection()

    /**
     * Display the first of these [AirshipEmbeddedInfo.instanceId]s that is pending, bypassing
     * ordering.
     *
     * An explicit order of preference, not a set: the earliest entry that is currently
     * pending wins, and the placeholder shows while none of them are.
     *
     * This is an **allow-list**. Pending content not named here is excluded entirely rather
     * than ordered last, so newly pending content won't display until the app includes it.
     *
     * Carries no closure, unlike [ByComparator], so it survives a bridge to a wrapper SDK —
     * an app that wants ordering it computes itself can observe the pending set, work out the
     * order, and hand back the IDs.
     *
     * @param instanceIds the [AirshipEmbeddedInfo.instanceId]s to display, most preferred
     * first.
     */
    public data class ByInstanceId(
        public val instanceIds: List<String>
    ) : AirshipEmbeddedSelection() {

        /**
         * Targets a single instance.
         *
         * @param instanceId the [AirshipEmbeddedInfo.instanceId] to display.
         */
        public constructor(instanceId: String) : this(listOf(instanceId))
    }

    /**
     * Let the on-device model choose which pending instance to display.
     *
     * The placeholder shows while the model decides. If the model is unavailable, has nothing
     * to tell the candidates apart, or returns no usable answer, [fallback] decides instead —
     * so an absent model never means an empty view.
     *
     * @param config How the model should choose.
     * @param fallback The selection to use when the model has no opinion.
     */
    public data class ByAi @JvmOverloads public constructor(
        public val config: Config,
        public val fallback: Fallback = Fallback.Priority
    ) : AirshipEmbeddedSelection() {

        /**
         * Configuration for [ByAi].
         *
         * @param prompt Instruction describing how to choose among the pending instances.
         *
         * The model scores each candidate 1-10 against this, against what each candidate's
         * layout says about itself (`content_description`), and against any user context an
         * app's context provider supplies. A context provider is optional: a prompt that ranks
         * on the content alone ("Prefer time-sensitive offers over evergreen content") works
         * without one, while a prompt about the person ("Show content matching the user's
         * interests") only differentiates if a provider is registered for
         * [com.urbanairship.ai.Usage.Companion].`embeddedSelection`.
         *
         * @param strategy How scores and candidate priorities combine into the final order.
         * @param minScoreThreshold The score the winner must reach for the model's answer to
         * be used at all; below it, [fallback] decides. Null accepts any score.
         * @param subjectHints Hints carried on the subject handed to the context provider.
         * Never rendered into the prompt.
         */
        public data class Config @JvmOverloads public constructor(
            public val prompt: String,
            public val strategy: Strategy = Strategy.SCORE_THEN_PRIORITY,
            public val minScoreThreshold: Int? = null,
            public val subjectHints: Map<String, String> = emptyMap()
        )

        /** How model scores and candidate priorities combine into a final ordering. */
        public enum class Strategy {

            /** Score leads; priority breaks ties between equal scores. */
            SCORE_THEN_PRIORITY,

            /** Priority leads; score breaks ties between equal priorities. */
            PRIORITY_THEN_SCORE
        }

        /**
         * A non-AI selection used when [ByAi] has no answer.
         *
         * A narrower set than [AirshipEmbeddedSelection] by design: falling back to AI would
         * be circular.
         */
        public sealed class Fallback {

            /** Priority ordering, with sticky last-displayed behavior. */
            public data object Priority : Fallback()

            /** Sort with the given comparator and display the first. */
            public data class ByComparator(
                public val comparator: Comparator<AirshipEmbeddedInfo>
            ) : Fallback()

            /**
             * Display the first of these [AirshipEmbeddedInfo.instanceId]s that is pending.
             *
             * An allow-list in preference order, as [AirshipEmbeddedSelection.ByInstanceId].
             */
            public data class ByInstanceId(
                public val instanceIds: List<String>
            ) : Fallback() {

                /**
                 * Targets a single instance.
                 *
                 * @param instanceId the [AirshipEmbeddedInfo.instanceId] to display.
                 */
                public constructor(instanceId: String) : this(listOf(instanceId))
            }

            /**
             * This fallback as the selection to apply.
             *
             * @hide
             */
            @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
            public val asSelection: AirshipEmbeddedSelection
                get() = when (this) {
                    Priority -> AirshipEmbeddedSelection.Priority
                    is ByComparator -> AirshipEmbeddedSelection.ByComparator(comparator)
                    is ByInstanceId -> AirshipEmbeddedSelection.ByInstanceId(instanceIds)
                }
        }
    }
}
