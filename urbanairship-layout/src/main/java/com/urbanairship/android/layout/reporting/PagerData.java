/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * Pager state.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerData implements JsonSerializable {

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

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .put(KEY_IDENTIFIER, identifier)
                .put(KEY_PAGE_INDEX, pageIndex)
                .put(KEY_PAGE_ID, pageId)
                .put(KEY_COUNT, count)
                .put(KEY_COMPLETED, completed)
                .build().toJsonValue();
    }

    private static final String KEY_IDENTIFIER = "identifier";
    private static final String KEY_PAGE_INDEX = "pageIndex";
    private static final String KEY_PAGE_ID = "pageId";
    private static final String KEY_COUNT = "count";
    private static final String KEY_COMPLETED = "completed";
}
