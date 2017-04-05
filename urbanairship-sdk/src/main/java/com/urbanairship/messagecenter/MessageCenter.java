/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;


import com.urbanairship.AirshipComponent;
import com.urbanairship.richpush.RichPushInbox;

/**
 * Primary interface for configuring the default
 * Message Center implementation.
 */
public class MessageCenter extends AirshipComponent {
    private RichPushInbox.Predicate predicate;

    public RichPushInbox.Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(RichPushInbox.Predicate predicate) {
        this.predicate = predicate;
    }
}
