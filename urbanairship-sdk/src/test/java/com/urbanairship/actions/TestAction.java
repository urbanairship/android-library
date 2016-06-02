/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions;

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

    @Override
    public ActionResult perform(ActionArguments arguments) {
        this.performCalled = true;
        this.runArgs = arguments;
        return result;
    }

    @Override
    public void onStart(ActionArguments arguments) {
        onStartCalled = true;
    }

    @Override
    public void onFinish(ActionArguments arguments, ActionResult result) {
        onFinishCalled = true;
    }

    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        return acceptsArguments;
    }
}

