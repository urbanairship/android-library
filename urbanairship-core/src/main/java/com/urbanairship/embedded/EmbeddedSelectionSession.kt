/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.RestrictTo

/**
 * What an [AirshipEmbeddedSelection.ByAi] selection remembers between emissions of a view's
 * pending list.
 *
 * Held by the view rather than by the flow that reads it, so it outlives a collection. A view
 * detaching and reattaching — recycled in a list, paged, rotated — restarts collection, and a
 * selection that forgot what it had chosen would blank the view, ask the model again, and be
 * free to reorder content the user was already looking at, which is the opposite of what
 * [AirshipEmbeddedSelection.ByAi.Config.allowDisplayInterruptions] promises.
 *
 * Scoped to one view and one config: a new session belongs with a changed
 * [AirshipEmbeddedSelection.ByAi.Config], since a reworded prompt or a new threshold should
 * re-ask rather than keep a ranking made under the old one. Sharing one across views would let
 * a view reorder a sibling's content.
 *
 * Not thread-safe, and doesn't need to be: it is read and written only from the collection of
 * the flow it was handed to.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedSelectionSession {

    /** Where the selection stands. */
    public var phase: Phase = Phase.Fallback

    /** The instance IDs last seen pending, so an arrival can be told from a departure. */
    public var knownIds: Set<String> = emptySet()

    /**
     * The order last put on screen, most preferred first.
     *
     * Consulted when `allowDisplayInterruptions` is false, so a re-rank can only append
     * arrivals rather than reshuffle what a user is already looking at.
     */
    public var committedOrder: List<String> = emptyList()

    /**
     * Where an AI selection stands, which decides what it shows.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public sealed interface Phase {

        /**
         * A ranking is in flight, so an empty order means "wait" rather than "fall back".
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data object Resolving : Phase

        /**
         * The model's answer, best first.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data class Ranked(public val ranking: List<String>) : Phase

        /**
         * The model had no answer, or was never asked, so the selection's fallback decides.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data object Fallback : Phase
    }
}
