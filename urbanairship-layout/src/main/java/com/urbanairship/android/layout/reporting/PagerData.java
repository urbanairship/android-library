/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Pager state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerData {

    private final String identifier;
    private final int pageIndex;
    private final String pageId;
    private final int count;
    private final boolean completed;

    public PagerData(@NonNull String identifier, int pageIndex, @NonNull String pageId, int count, boolean completed) {
        this.identifier = identifier;
        this.pageIndex = pageIndex;
        this.pageId = pageId;
        this.count = count;
        this.completed = completed;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public String getPageId() {
        return pageId;
    }

    public int getIndex() {
        return pageIndex;
    }

    public int getCount() {
        return count;
    }

    public boolean isCompleted() {
        return completed;
    }

    @Override
    @NonNull
    public String toString() {
        return "PagerData{" +
            "identifier='" + identifier + '\'' +
            ", pageIndex=" + pageIndex +
            ", pageId=" + pageId +
            ", count=" + count +
            ", completed=" + completed +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PagerData pagerData = (PagerData) o;
        return pageIndex == pagerData.pageIndex &&
                count == pagerData.count &&
                completed == pagerData.completed &&
                Objects.equals(identifier, pagerData.identifier) &&
                Objects.equals(pageId, pagerData.pageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, pageIndex, pageId, count, completed);
    }
}
