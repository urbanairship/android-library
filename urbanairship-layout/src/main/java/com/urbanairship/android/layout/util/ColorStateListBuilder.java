/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.res.ColorStateList;

import java.util.ArrayList;
import java.util.List;

public class ColorStateListBuilder {
    private static final int[] EMPTY_STATE_SET = new int[]{ };


    private final List<Integer> colors = new ArrayList<>();
    private final List<int[]> states = new ArrayList<>();

    public ColorStateListBuilder add(int color, int... states) {
        this.colors.add(color);
        this.states.add(states);
        return this;
    }

    public ColorStateListBuilder add(int color) {
        this.colors.add(color);
        this.states.add(EMPTY_STATE_SET);
        return this;
    }

    public ColorStateList build() {
        return new ColorStateList(getStates(), getColors());
    }

    private int[] getColors() {
        int[] array = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            array[i] = colors.get(i);
        }
         return array;
    }

    private int[][] getStates() {
        int[][] array = new int[states.size()][1];
        for (int i = 0; i < states.size(); i++) {
            array[i] = states.get(i);
        }
        return array;
    }
}
