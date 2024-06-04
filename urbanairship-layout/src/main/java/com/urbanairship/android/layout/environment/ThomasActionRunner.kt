package com.urbanairship.android.layout.environment

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ThomasActionRunner {
    public fun run(actions: Map<String, JsonValue>, state: LayoutData)
}
