package com.urbanairship.automation.rewrite.inappmessage.analytics.events

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface InAppEvent {
    public val name: String
    public val data:  JsonSerializable?
}
