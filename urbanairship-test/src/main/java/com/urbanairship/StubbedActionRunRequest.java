/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.Bundle;
import android.os.Looper;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;

import androidx.annotation.NonNull;

/**
 * Stubbed action run request for testing. All methods are overridden to no-op.
 */
public class StubbedActionRunRequest extends ActionRunRequest {

    public StubbedActionRunRequest() {
        super(new Action() {
            @NonNull
            @Override
            public ActionResult perform(@NonNull ActionArguments arguments) {
                return ActionResult.newEmptyResult();
            }
        });
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
    public ActionRunRequest setSituation(Action.Situation situation) {
        return this;
    }

    @NonNull
    @Override
    public ActionResult runSync() {
        return ActionResult.newEmptyResult();
    }

    @Override
    public void run() {
    }

    @Override
    public void run(ActionCompletionCallback callback) {
    }

    @Override
    public void run(Looper looper, ActionCompletionCallback callback) {
    }

}
