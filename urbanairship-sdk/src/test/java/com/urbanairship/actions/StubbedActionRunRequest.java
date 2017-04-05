/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;

/**
 * Stubbed action run request for testing. All methods are overridden to no-op.
 */
public class StubbedActionRunRequest extends ActionRunRequest {

    public StubbedActionRunRequest() {
        super(null);
    }

    @NonNull
    @Override
    public ActionRunRequest setValue(Object actionValue) {
        return this;
    }

    @NonNull
    @Override
    public ActionRunRequest setMetadata(Bundle metadata) {
        return this;
    }

    @NonNull
    @Override
    public ActionRunRequest setSituation(@Action.Situation int situation) {
        return this;
    }

    @NonNull
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
