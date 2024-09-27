/* Copyright Airship and Contributors */
package com.urbanairship.iam

import com.urbanairship.iam.content.InAppMessageDisplayContent

/** Interface used to extend in-app message content. */
public interface InAppMessageContentExtender {

    /**
     * Extends the content for in-app messages.
     *
     * @param message The in-app message.
     * @return The extended in-app message display content.
     */
    public fun extend(message: InAppMessage): InAppMessageDisplayContent = message.displayContent
}
