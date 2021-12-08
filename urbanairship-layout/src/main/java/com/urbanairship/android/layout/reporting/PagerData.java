/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import androidx.annotation.RestrictTo;

/**
 * Pager state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerData {

    private final String identifier;
    private final int index;
    private final int count;
    private final boolean completed;

    public PagerData(String identifier, int index, int count, boolean completed) {
        this.identifier = identifier;
        this.index = index;
        this.count = count;
        this.completed = completed;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getIndex() {
        return index;
    }

    public int getCount() {
        return count;
    }

    public boolean isCompleted() {
        return completed;
    }
}
