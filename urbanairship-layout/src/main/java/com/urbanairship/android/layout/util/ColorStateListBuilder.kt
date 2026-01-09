/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.res.ColorStateList

public class ColorStateListBuilder public constructor() {

    private val colors = mutableListOf<Int>()
    private val states = mutableListOf<IntArray>()

    public fun add(color: Int, vararg states: Int): ColorStateListBuilder {
        this.colors.add(color)
        this.states.add(states)
        return this
    }

    public fun add(color: Int): ColorStateListBuilder {
        this.colors.add(color)
        this.states.add(EMPTY_STATE_SET)
        return this
    }

    public fun build(): ColorStateList {
        return ColorStateList(states.toTypedArray(), colors.toIntArray())
    }

    public companion object {
        private val EMPTY_STATE_SET = intArrayOf()
    }
}
