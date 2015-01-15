/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


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

