/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

/**
 * Refuses a placement move that would repeat a back-and-forth.
 *
 * A placement's size can set the frame its conditions read: one placement's frame chooses another
 * whose frame chooses the first. After `A → B` then `B → A` in one window, a second `A → B` is
 * refused and `A` stays. Moves in a different window start over.
 *
 * @param P the placement type, compared by identity.
 * @param W the window the moves are made in.
 */
internal class PlacementCycleBreaker<P : Any, W : Any> {

    private var window: W? = null
    private var last: Pair<P, P>? = null
    private var beforeLast: Pair<P, P>? = null

    /**
     * Returns whether to move from [from] to [to] in [window], recording the move when it does.
     *
     * @param from the placement shown.
     * @param to the placement the conditions choose.
     * @param window the window the move would be made in.
     * @return false when the move repeats the one before a move straight back.
     */
    fun allows(from: P, to: P, window: W): Boolean {
        if (window != this.window) {
            reset()
            this.window = window
        }
        val move = from to to
        if (move.sameAs(beforeLast) && (to to from).sameAs(last)) return false
        beforeLast = last
        last = move
        return true
    }

    /** Forgets the moves made, as when the configuration changes. */
    fun reset() {
        window = null
        last = null
        beforeLast = null
    }

    private fun Pair<P, P>.sameAs(other: Pair<P, P>?): Boolean =
        other != null && first === other.first && second === other.second
}
