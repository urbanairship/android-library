/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;

import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class ActionTest extends BaseTestCase {

    /**
     * Test running an action calls onStart, perform, and onFinish
     * with the expected inputs.
     */
    @Test
    public void testRun() throws ActionValueException {
        Bundle metadata = new Bundle();
        metadata.putString("metadata_key", "metadata_value");

        final ActionResult expectedResult = ActionResult.newResult(ActionValue.wrap("result"));
        final ActionArguments originalArguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "value", metadata);


        // Create a test action that verifies the result, handle, and arguments
        // in each method
        TestAction action = new TestAction(true, expectedResult) {
            @Override
            public ActionResult perform(ActionArguments arguments) {
                assertEquals("Action arguments is a different instance then the passed in arguments",
                        arguments, originalArguments);

                assertEquals("Bundle does not contain the passed in metadata", "metadata_value",
                        arguments.getMetadata().get("metadata_key"));

                return super.perform(arguments);
            }

            @Override
            public void onStart(ActionArguments arguments) {
                super.onStart(arguments);

                assertEquals("Bundle does not contain the passed in metadata",
                        arguments.getMetadata().get("metadata_key"), "metadata_value");

                assertEquals("Action arguments is a different instance then the passed in arguments",
                        arguments, originalArguments);
            }

            @Override
            public void onFinish(ActionArguments arguments, ActionResult result) {
                super.onFinish(arguments, result);

                assertEquals("Bundle does not contain the passed in metadata",
                        arguments.getMetadata().getString("metadata_key"), "metadata_value");

                assertEquals("Action arguments is a different instance then the passed in arguments",
                        arguments, originalArguments);

                assertEquals("Action result is a different instance then then the returned results",
                        result, expectedResult);
            }
        };

        // Verify the result is passed back properly
        ActionResult results = action.run(originalArguments);

        assertEquals("Action result is unexpected", expectedResult, results);
        assertEquals("Result should have COMPLETED status",
                ActionResult.STATUS_COMPLETED, expectedResult.getStatus());

        // Verify the methods were called
        assertTrue("Action.onStart is not being called", action.onStartCalled);
        assertTrue("Action.perform is not being called", action.performCalled);
        assertTrue("Action.onFinish is not being called", action.onFinishCalled);
    }

    /**
     * Test running an action that does not
     * accept the arguments.
     */
    @Test
    public void testRunBadArgs() throws ActionValueException {
        ActionResult performResult = ActionResult.newResult(ActionValue.wrap("result"));
        TestAction action = new TestAction(false, performResult);

        ActionResult badArgsResult = action.run(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "value"));

        assertTrue("Does not accept arguments should return a 'null' result value", badArgsResult.getValue().isNull());

        assertEquals("Result should have an rejected arguemnts status",
                ActionResult.STATUS_REJECTED_ARGUMENTS, badArgsResult.getStatus());

        assertFalse("Does not accept arguments should not call any of the actions perform methods.",
                action.onStartCalled);
        assertFalse("Does not accept arguments should not call any of the actions perform methods.",
                action.performCalled);
        assertFalse("Does not accept arguments should not call any of the actions perform methods.",
                action.onFinishCalled);
    }


    /**
     * Test unexpected runtime exceptions being thrown when an action is performing
     * returns a result with the exception as the value.
     */
    @Test
    public void testRunPerformException() throws ActionValueException {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "value");
        ActionResult performResult = ActionResult.newResult(ActionValue.wrap("result"));

        final IllegalStateException exception = new IllegalStateException("oh no!");

        TestAction action = new TestAction(true, performResult) {
            @Override
            public ActionResult perform(ActionArguments arguments) {
                super.perform(arguments);
                throw exception;
            }
        };

        ActionResult result = action.run(args);

        assertEquals("Result should pass back exception as the value", exception, result.getException());
        assertTrue("Result should be 'null'", result.getValue().isNull());
        assertEquals("Result should have an error status",
                ActionResult.STATUS_EXECUTION_ERROR, result.getStatus());

        assertTrue("Action.onStart is not being called", action.onStartCalled);
        assertTrue("Action.perform is not being called", action.performCalled);
        assertFalse("Action.onFinish should not be called if a runtime exception is thrown", action.onFinishCalled);
    }

    /**
     * Test that when perform returns null, an empty result is generated and returned.
     */
    @Test
    public void testRunPerformNullResult() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "value");

        TestAction action = new TestAction(true, null);

        ActionResult result = action.run(args);
        assertNotNull("Result should never be null", result);
        assertTrue("Result should be 'null'", result.getValue().isNull());
        assertEquals("Result should have the COMPLETED status",
                ActionResult.STATUS_COMPLETED, result.getStatus());
    }

    /**
     * Test starting an activity for a result
     */
    @Test
    public void testStartActivityForResult() throws InterruptedException {
        // Set up expected result from the activity
        final int expectedCode = 1;
        final Intent expectedData = new Intent();

        // Create an action that starts an activity for a result
        final Intent activityIntent = new Intent();
        final TestAction action = new TestAction() {
            @Override
            public ActionResult perform(ActionArguments arguments) {
                Action.ActivityResult result = startActivityForResult(activityIntent);

                // Verify the result and data
                assertEquals("Unexpected result code", expectedCode, result.getResultCode());
                assertEquals("Unexpected result data", expectedData, result.getIntent());

                return super.perform(arguments);
            }
        };

        // Run the action in a different thread because starting activity
        // for result blocks
        Thread actionThread = new Thread(new Runnable() {
            public void run() {
                action.run(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "arg"));
            }
        });
        actionThread.start();

        // Wait til we have a started activity from the action thread
        ShadowApplication application = ShadowApplication.getInstance();
        for (int i = 0; i < 10; i++) {
            Intent intent = application.peekNextStartedActivity();
            if (intent != null && intent.getComponent().getClassName().equals(ActionActivity.class.getName())) {
                break;
            }
            Thread.sleep(10);
        }

        // Verify the intent
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(intent.getComponent().getClassName(), ActionActivity.class.getName());
        assertEquals(activityIntent, intent.getParcelableExtra(ActionActivity.START_ACTIVITY_INTENT_EXTRA));

        ResultReceiver receiver = intent.getParcelableExtra(ActionActivity.RESULT_RECEIVER_EXTRA);
        assertNotNull(receiver);

        // Send the result
        Bundle bundle = new Bundle();
        bundle.putParcelable(ActionActivity.RESULT_INTENT_EXTRA, expectedData);
        receiver.send(expectedCode, bundle);

        // Thread should be able to finish
        actionThread.join(100);
        assertFalse("Thread is not finishing", actionThread.isAlive());

        // Make sure the perform was actually called
        assertTrue("Action did not perform", action.performCalled);
    }
}
