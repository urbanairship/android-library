package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireList

/**
 * Pager branching directives. These control the branching behavior of the
 * [com.urbanairship.android.layout.model.PagerController].
 */
internal data class PagerControllerBranching(
    /**
     * Determines when a pager is completed, since we can not rely on the last
     * page meaning "completed" in branching. The given [Completion] are
     * evaluated to determine that completion. Evaluated in order, first match
     * wins.
     */
    val completions: List<Completion>
): JsonSerializable {
    companion object {
        private const val PAGER_COMPLETIONS = "pager_completions"

        @Throws(JsonException::class)
        fun from(json: JsonValue): PagerControllerBranching {
            val content = json.requireMap()

            return PagerControllerBranching(
                completions = content.requireList(PAGER_COMPLETIONS).map(Completion::from)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        PAGER_COMPLETIONS to completions
    ).toJsonValue()

    /**
     * Pager completion directives; used to determine when a pager has been
     * completed, and optional actions to take upon completion.
     */
    internal data class Completion(
        /**
         * Predicate to match when evaluating completion. If not provided, it is an
         * implicit match.
         */
        val predicate: JsonPredicate?,
        /**
         * State actions to run when the pager completes.
         */
        val stateActions: List<StateAction>?
    ): JsonSerializable {
        companion object {
            private const val WHEN_STATE_MATCHES = "when_state_matches"
            private const val STATE_ACTIONS = "state_actions"

            @Throws(JsonException::class)
            fun from(json: JsonValue): Completion {
                val content = json.requireMap()

                return Completion(
                    predicate = content.get(WHEN_STATE_MATCHES)?.let(JsonPredicate::parse),
                    stateActions = content.get(STATE_ACTIONS)?.requireList()?.map(StateAction::fromJson)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            WHEN_STATE_MATCHES to predicate,
            STATE_ACTIONS to stateActions
        ).toJsonValue()
    }
}

/**
 * Page branching directives, used to evaluate page behavior when the page's
 * parent controller has branching enabled.
 */
internal data class PageBranching(
    /**
     * Controls which page should be used as the next page; only evaluated when
     * the [com.urbanairship.android.layout.model.PagerController] is configured for branching logic.
     * Predicates are evaluated in order, and the first matching predicate is used. If no
     * predicates are matched, or if this directive is not present, proceeding to
     * the next page is blocked.
     */
    val nextPageSelectors: List<PageSelector>?,
    /**
     * Controls if moving to the previous page is allowed; only evaluated when the
     * [com.urbanairship.android.layout.model.PagerController] is configured for branching logic. If this directive is
     * not present, moving to the previous page is allowed.
     */
    val previousPageControl: PreviousPageControl?
): JsonSerializable {
    companion object {
        private const val NEXT_PAGE = "next_page"
        private const val PREVIOUS_PAGE_DISABLED = "previous_page_disabled"
        private const val SELECTORS = "selectors"

        @Throws(JsonException::class)
        fun from(json: JsonValue): PageBranching {
            val content = json.requireMap()

            return PageBranching(
                nextPageSelectors = content.get(NEXT_PAGE)
                    ?.requireMap()
                    ?.get(SELECTORS)
                    ?.requireList()
                    ?.map(PageSelector::from),
                previousPageControl = content.get(PREVIOUS_PAGE_DISABLED)?.let(PreviousPageControl::from)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        NEXT_PAGE to jsonMapOf(SELECTORS to nextPageSelectors),
        PREVIOUS_PAGE_DISABLED to previousPageControl
    ).toJsonValue()

    internal data class PageSelector(
        /**
         * Predicate which is matched for the given `page_id`. When `undefined`, it is
         * an implicit match.
         */
        val predicate: JsonPredicate?,
        /**
         * ID of the page to be used as the next page.
         */
        val pageId: String
    ): JsonSerializable {
        companion object {
            private const val WHEN_STATE_MATCHES = "when_state_matches"
            private const val PAGE_ID = "page_id"

            @Throws(JsonException::class)
            fun from(json: JsonValue): PageSelector {
                val content = json.requireMap()

                return PageSelector(
                    predicate = content.get(WHEN_STATE_MATCHES)?.let(JsonPredicate::parse),
                    pageId = content.requireField(PAGE_ID)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            WHEN_STATE_MATCHES to predicate,
            PAGE_ID to pageId
        ).toJsonValue()
    }

    internal data class PreviousPageControl(
        /**
         * When provided, moving to the previous page is disabled when the predicate
         * matches.
         */
        val predicate: JsonPredicate?,
        /**
         * When set `true`, moving to the previous page is always disabled.
         */
        val alwaysDisabled: Boolean?
    ) : JsonSerializable {

        companion object {
            private const val WHEN_STATE_MATCHES = "when_state_matches"
            private const val ALWAYS = "always"

            @Throws(JsonException::class)
            fun from(json: JsonValue): PreviousPageControl {
                val content = json.requireMap()

                return PreviousPageControl(
                    predicate = content.get(WHEN_STATE_MATCHES)?.let(JsonPredicate::parse),
                    alwaysDisabled = content.optionalField(ALWAYS)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            WHEN_STATE_MATCHES to predicate,
            ALWAYS to alwaysDisabled
        ).toJsonValue()
    }
}


