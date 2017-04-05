/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class ActionRunRequestTest extends BaseTestCase {

    private ActionRegistry actionRegistry;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };

        ActionRunRequest.executor = executor;
        actionRegistry = new ActionRegistry();
    }

    @After
    public void cleanup() {
        ActionRunRequest.executor = Executors.newCachedThreadPool();
    }

    /**
     * Test running an action
     */
    @Test
    public void testRunAction() throws ActionValueException {
        ActionResult result = ActionResult.newResult(ActionValue.wrap("result"));
        TestAction action = new TestAction(true, result);

        // Run the action without a callback
        ActionRunRequest.createRequest(action)
                        .setValue("val")
                        .run();

        assertTrue("Action failed to run", action.performCalled);
        assertNull("Action name should be null", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
    }

    /**
     * Test running an action with a callback.
     */
    @Test
    public void testRunActionWithCallback() throws ActionValueException {
        ActionResult result = ActionResult.newResult(ActionValue.wrap("result"));
        TestActionCompletionCallback callback = new TestActionCompletionCallback();
        TestAction action = new TestAction(true, result);

        // Run the action with a callback
        ActionRunRequest.createRequest(action)
                        .setValue("val")
                        .run(callback);

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result was not called with expected result", result, callback.lastResult);
        assertEquals("Run args and the callback args should match", action.runArgs, callback.lastArguments);
        assertNull("Action name should be null", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
    }

    /**
     * Test running an action from the registry.
     */
    @Test
    public void testRunActionFromRegistry() throws ActionValueException {
        ActionResult result = ActionResult.newResult(ActionValue.wrap("result"));
        TestAction action = new TestAction(true, result);

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // Run the action without a callback
        ActionRunRequest.createRequest("action!", actionRegistry)
                        .setValue("val")
                        .run();

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Wrong action name", "action!", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
    }

    /**
     * Test running an action from the registry with a callback.
     */
    @Test
    public void testRunActionFromRegistryWithCallback() throws ActionValueException {
        ActionResult result = ActionResult.newResult(ActionValue.wrap("result"));
        TestAction action = new TestAction(true, result);
        TestActionCompletionCallback callback = new TestActionCompletionCallback();

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // Run the action with a callback
        ActionRunRequest.createRequest("action!", actionRegistry)
                        .setValue("val")
                        .run(callback);

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result was not called with expected result", result, callback.lastResult);
        assertEquals("Wrong action name", "action!", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
    }

    /**
     * Test running an action synchronously
     */
    @Test
    public void testRunActionSync() {
        TestAction action = new TestAction();

        // Run the action by name
        ActionResult result = ActionRunRequest.createRequest(action).runSync();
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result status should be COMPLETED", ActionResult.STATUS_COMPLETED, result.getStatus());
    }

    /**
     * Test running an action synchronously from the registry
     */
    @Test
    public void testRunSyncFromRegistry() {
        TestAction action = new TestAction();

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // Run the action by name
        ActionResult result = ActionRunRequest.createRequest("action!", actionRegistry).runSync();

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Wrong action name", "action!", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
        assertEquals("Result status should be COMPLETED", ActionResult.STATUS_COMPLETED, result.getStatus());
    }

    /**
     * Test running a null action
     */
    @Test
    public void testRunNullAction() {
        // Expect the exception
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Unable to run null action");

        ActionRunRequest.createRequest((Action) null);
    }

    /**
     * Test trying to set the action value to something that is not ActionValue wrappable.
     */
    @Test
    public void testInvalidActionValueFromObject() {
        // Expect the exception
        exception.expect(IllegalArgumentException.class);

        // Try setting a invalid value
        ActionRunRequest.createRequest("action").setValue(new Object());
    }


    /**
     * Test running an action from the registry with a predicate
     * that rejects the arguments
     */
    @Test
    public void testRunActionFromRegistryWithPredicate() {
        TestAction action = new TestAction(true, null);
        TestActionCompletionCallback callback = new TestActionCompletionCallback();

        // Register the action
        ActionRegistry.Entry entry = actionRegistry.registerAction(action, "action!");

        // Set a predicate that rejects all arguments
        entry.setPredicate(new ActionRegistry.Predicate() {
            @Override
            public boolean apply(ActionArguments arguments) {
                return false;
            }
        });

        ActionRunRequest.createRequest("action!", actionRegistry)
                        .setValue("val")
                        .run(callback);

        ActionResult result = callback.lastResult;

        assertTrue("Predicate rejecting args should have the result value be 'null'",
                result.getValue().isNull());

        assertEquals("Result should have an rejected argument status",
                ActionResult.STATUS_REJECTED_ARGUMENTS, result.getStatus());

        assertTrue("Callback is not being called", callback.onFinishCalled);
    }

    /**
     * Test running an action without setting the situation defaults to Action.SITUATION_MANUAL_INVOCATION
     */
    @Test
    public void testRunDefaultSituation() {
        TestAction action = new TestAction(true, null);

        ActionRunRequest.createRequest(action).runSync();

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Situation should default to MANUAL_INVOCATION", Action.SITUATION_MANUAL_INVOCATION, action.runArgs.getSituation());
    }

    /**
     * Test running an action that does not exist in the registry
     */
    @Test
    public void testRunActionNoEntry() {
        ActionResult result = ActionRunRequest.createRequest("action", actionRegistry).runSync();

        assertTrue("Running an action that does not exist should return a 'null' result",
                result.getValue().isNull());

        assertEquals("Result should have an error status",
                ActionResult.STATUS_ACTION_NOT_FOUND, result.getStatus());
    }

    /**
     * Test action callback is called on the callers thread
     */
    @Test
    public void testCallbackHappensOnCallersThread() throws InterruptedException {
        // Get the looper
        ShadowLooper looper = Shadows.shadowOf(Looper.getMainLooper());

        // Run any tasks in its queue
        looper.runToEndOfTasks();

        // Run an action with a callback
        Action testAction = new TestAction();
        TestActionCompletionCallback callback = new TestActionCompletionCallback();

        // Assign a single thread executor to the ActionRunRequests
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ActionRunRequest.executor = executorService;

        ActionRunRequest.createRequest(testAction)
                        .run(callback);

        // Wait for the action to finish running
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        // Check that we have a message in the looper's queue
        assertEquals(1, looper.getScheduler().size());
        assertFalse("Callback should not be called yet", callback.onFinishCalled);

        // Run all messages
        looper.runToEndOfTasks();
        assertEquals(0, looper.getScheduler().size());
        assertTrue("Callback on finish is not being called", callback.onFinishCalled);
    }

    /**
     * Test setting metadata will be combined with the registry name.
     */
    @Test
    public void testMetadata() {
        TestAction action = new TestAction();
        actionRegistry.registerAction(action, "action!");

        // Create metadata
        Bundle metadata = new Bundle();
        metadata.putString("so", "meta");

        // Run the action by name
        ActionRunRequest.createRequest("action!", actionRegistry)
                        .setMetadata(metadata)
                        .runSync();

        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Wrong action name", "action!", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
        assertEquals("Wrong action name", "action!", action.runArgs.getMetadata().get(ActionArguments.REGISTRY_ACTION_NAME_METADATA));
        assertEquals("Missing metadata", "meta", action.runArgs.getMetadata().getString("so"));
    }

    private class TestActionCompletionCallback implements ActionCompletionCallback {

        public ActionResult lastResult;
        public boolean onFinishCalled = false;
        public ActionArguments lastArguments;

        @Override
        public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
            lastResult = result;
            lastArguments = arguments;
            onFinishCalled = true;
        }
    }
}
