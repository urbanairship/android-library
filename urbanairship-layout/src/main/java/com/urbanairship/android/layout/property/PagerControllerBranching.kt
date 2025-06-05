package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.property.PagerControllerBranching.Completion
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
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
                    stateActions = content.optionalList(STATE_ACTIONS)?.map(StateAction::fromJson)
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
    val nextPageSelectors: List<PageSelector>?
): JsonSerializable {
    companion object {
        private const val NEXT_PAGE = "next_page"
        private const val SELECTORS = "selectors"

        @Throws(JsonException::class)
        fun from(json: JsonValue): PageBranching {
            val content = json.requireMap()

            return PageBranching(
                nextPageSelectors = content.optionalMap(NEXT_PAGE)
                    ?.requireList(SELECTORS)
                    ?.map(PageSelector::from)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        NEXT_PAGE to jsonMapOf(SELECTORS to nextPageSelectors),
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
}
