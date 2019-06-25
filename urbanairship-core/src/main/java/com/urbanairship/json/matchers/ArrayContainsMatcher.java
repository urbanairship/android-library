/* Copyright Airship and Contributors */

package com.urbanairship.json.matchers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

/**
 * Array contains matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ArrayContainsMatcher extends ValueMatcher {

    /**
     * Json key for the predicate.
     */
    @NonNull
    public static final String ARRAY_CONTAINS_KEY = "array_contains";

    /**
     * Json key for the index.
     */
    @NonNull
    public static final String INDEX_KEY = "index";

    private final Integer index;
    private final JsonPredicate predicate;

    /**
     * Default constructor.
     *
     * @param predicate The predicate.
     * @param index The optional index.
     */
    public ArrayContainsMatcher(@NonNull JsonPredicate predicate, @Nullable Integer index) {
        this.predicate = predicate;
        this.index = index;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(ARRAY_CONTAINS_KEY, predicate)
                      .putOpt(INDEX_KEY, index)
                      .build()
                      .toJsonValue();
    }

    @Override
    protected boolean apply(@NonNull JsonValue jsonValue, boolean ignoreCase) {
        if (!jsonValue.isJsonList()) {
            return false;
        }

        JsonList list = jsonValue.optList();

        if (index != null) {
            if (index < 0 || index >= list.size()) {
                return false;
            }

            return predicate.apply(list.get(index));
        }

        for (JsonValue value : list) {
            if (predicate.apply(value)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ArrayContainsMatcher that = (ArrayContainsMatcher) o;

        if (index != null ? !index.equals(that.index) : that.index != null) {
            return false;
        }
        return predicate.equals(that.predicate);
    }

    @Override
    public int hashCode() {
        int result = index != null ? index.hashCode() : 0;
        result = 31 * result + predicate.hashCode();
        return result;
    }

}
