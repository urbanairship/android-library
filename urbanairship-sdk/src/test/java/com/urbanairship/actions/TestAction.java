/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

/**
 * Test action that tracks what methods were called
 */
public class TestAction extends Action {

    private final ActionResult result;
    public boolean onStartCalled = false;
    public boolean onFinishCalled = false;
    public boolean performCalled = false;

    public ActionArguments runArgs;

    private boolean acceptsArguments = false;

    public TestAction() {
        this(true, null);
    }

    public TestAction(boolean acceptsArguments, ActionResult result) {
        this.acceptsArguments = acceptsArguments;
        this.result = result;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        this.performCalled = true;
        this.runArgs = arguments;
        return result;
    }

    @Override
    public void onStart(@NonNull ActionArguments arguments) {
        onStartCalled = true;
    }

    @Override
    public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
        onFinishCalled = true;
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        return acceptsArguments;
    }
}

