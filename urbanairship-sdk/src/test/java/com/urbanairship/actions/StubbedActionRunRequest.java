/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Looper;

/**
 * Stubbed action run request for testing. All methods are overridden to no-op.
 */
public class StubbedActionRunRequest extends ActionRunRequest {

    public StubbedActionRunRequest() {
        super(null);
    }

    @Override
    public ActionRunRequest setValue(Object actionValue) {
        return this;
    }

    @Override
    public ActionRunRequest setMetadata(Bundle metadata) {
        return this;
    }

    @Override
    public ActionRunRequest setSituation(@Action.Situation int situation) {
        return this;
    }

    @Override
    public ActionResult runSync() {
        return ActionResult.newEmptyResult();
    }

    @Override
    public void run() { }

    @Override
    public void run(ActionCompletionCallback callback) { }

    @Override
    public void run(ActionCompletionCallback callback, Looper looper) { }

}
