/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json.matchers;

import android.support.annotation.RestrictTo;

import com.urbanairship.Predicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * Interface for value matchers.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface JsonValueMatcher extends JsonSerializable, Predicate<JsonValue> {}
