package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireList

internal data class PagerControllerBranching(
    val pagerCompletion: List<PagerCompletion>
) {
    companion object {
        @Throws(JsonException::class)
        fun from(json: JsonMap): PagerControllerBranching {
            return PagerControllerBranching(
                pagerCompletion = json.requireList("pager_completions").map {
                    PagerCompletion.from(it.requireMap())
                }
            )
        }
    }

    internal data class PagerCompletion(
        val whenStateMatches: JsonPredicate?,
        val stateActions: List<StateAction>?
    ) {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): PagerCompletion {
                return PagerCompletion(
                    whenStateMatches = JsonPredicate.parse(json.optionalField("when_state_matches")),
                    stateActions = json.optionalList("state_actions")?.map {
                        StateAction.fromJson(it.requireMap())
                    }
                )
            }
        }
    }

    internal data class PageBranching(
        val nextPage: Map<String, JsonValue>?,
        val previousPageDisabled: Map<String, JsonValue>?
    ) {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): PageBranching {
                return PageBranching(
                    nextPage = json.optionalMap("next_page")?.map,
                    previousPageDisabled = json.optionalMap("state_actions")?.map
                )
            }
        }
    }

    internal data class NextPageSelector(
        val whenStateMatches: JsonPredicate?,
        val pageId: String
    ) {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): NextPageSelector {
                return NextPageSelector(
                    whenStateMatches = json.optionalField("when_state_matches"),
                    pageId = json.requireField("page_id")
                )
            }
        }
    }
}
