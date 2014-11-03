package com.urbanairship.actions;

import android.os.Looper;

import com.android.internal.util.Predicate;
import com.urbanairship.RobolectricGradleTestRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class ActionRunnerTest {

    private ActionRunner actionRunner;
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

        actionRegistry = new ActionRegistry();
        actionRunner = new ActionRunner(actionRegistry, executor);
    }

    /**
     * Test running an action
     */
    @Test
    public void testRunAction() {
        ActionResult result = ActionResult.newResult("result");
        TestAction action = new TestAction(true, result);

        // Run the action without a callback
        actionRunner.runAction(action, new ActionArguments(Situation.MANUAL_INVOCATION, "val"));
        assertTrue("Action failed to run", action.performCalled);
        assertNull("Action name should be null", action.actionName);

        // Run the action with a callback
        TestActionCompletionCallback callback = new TestActionCompletionCallback();
        action = new TestAction(true, result);

        actionRunner.runAction(action, new ActionArguments(Situation.MANUAL_INVOCATION, "val"), callback);
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result was not called with expected result", result, callback.lastResult);
        assertNull("Action name should be null", action.actionName);
    }

    /**
     * Test running a null action
     */
    @Test
    public void testRunNullAction() {
        // Expect the exception
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Unable to run null action");
        actionRunner.runAction((Action) null, new ActionArguments(Situation.MANUAL_INVOCATION, "val"), null);
    }

    /**
     * Test running an action from the registry
     */
    @Test
    public void testRunActionFromRegistry() {
        ActionResult result = ActionResult.newResult("result");
        TestAction action = new TestAction(true, result);

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // Run the action without a callback
        actionRunner.runAction("action!", new ActionArguments(Situation.MANUAL_INVOCATION, "val"));
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Wrong action name", "action!", action.actionName);

        // Run the action with a callback
        TestActionCompletionCallback callback = new TestActionCompletionCallback();
        action = new TestAction(true, result);
        actionRegistry.registerAction(action, "action!");

        actionRunner.runAction("action!", new ActionArguments(Situation.MANUAL_INVOCATION, "val"), callback);
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result was not called with expected result", result, callback.lastResult);
        assertEquals("Wrong action name", "action!", action.actionName);
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
        entry.setPredicate(new Predicate<ActionArguments>() {
            @Override
            public boolean apply(ActionArguments arguments) {
                return false;

            }
        });


        // Run the action without a callback
        actionRunner.runAction("action!", new ActionArguments(Situation.MANUAL_INVOCATION, "val"), callback);

        ActionResult result = callback.lastResult;

        assertNull("Predicate rejecting args should have the result value be null",
                result.getValue());

        assertEquals("Result should have an rejected argument status",
                ActionResult.Status.REJECTED_ARGUMENTS, result.getStatus());

        assertTrue("Callback is not being called", callback.onFinishCalled);
    }

    /**
     * Test trying to run an action with null arguments
     */
    @Test
    public void testRunActionNullArgs() {
        ActionResult result = ActionResult.newResult("result");
        TestAction action = new TestAction(true, result);
        TestActionCompletionCallback callback = new TestActionCompletionCallback();

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // With null arguments
        actionRunner.runAction(action, null);
        assertFalse("Action.perform should not be called with null args", action.performCalled);

        // With null arguments and a callback
        actionRunner.runAction(action, null, callback);
        assertFalse("Action.perform should not be called with null args", action.performCalled);
        assertNull("Null arg should call the callback with a null result value", callback.lastResult.getValue());
        assertTrue("Callback is not being called", callback.onFinishCalled);

        // Try running the action from the registry
        actionRunner.runAction("action!", null);
        assertFalse("Action.perform should not be called with null args", action.performCalled);

        // With a callback
        callback = new TestActionCompletionCallback();
        actionRunner.runAction(action, null, callback);
        assertFalse("Action.perform should not be called with null args", action.performCalled);
        assertNull("Null arg should call the callback with a null result value", callback.lastResult.getValue());
        assertTrue("Callback is not being called", callback.onFinishCalled);
    }

    /**
     * Test running an action that does not exist in the registry
     */
    @Test
    public void testRunActionNoEntry() {
        TestActionCompletionCallback callback = new TestActionCompletionCallback();
        actionRunner.runAction("action!", new ActionArguments(Situation.MANUAL_INVOCATION, "result"), callback);

        ActionResult result = callback.lastResult;

        assertNull("Running an action that does not exist should return a null result",
                result.getValue());

        assertEquals("Result should have an error status",
                ActionResult.Status.ACTION_NOT_FOUND, result.getStatus());

        assertTrue("Callback is not being called", callback.onFinishCalled);
    }

    /**
     * Test action callback is called on the callers thread
     */
    @Config(reportSdk = 5)
    @Test
    public void testCallbackHappensOnCallersThread() throws InterruptedException {
        // Get the looper
        ShadowLooper looper = Robolectric.shadowOf(Looper.getMainLooper());

        // Run any tasks in its queue
        looper.runToEndOfTasks();

        // Recreate the Action Manager with a single thread executor
        ExecutorService executor = Executors.newSingleThreadExecutor();
        actionRunner = new ActionRunner(actionRegistry, executor);

        // Run an action with a callback
        Action testAction = new TestAction();
        TestActionCompletionCallback callback = new TestActionCompletionCallback();
        actionRunner.runAction(testAction, new ActionArguments(Situation.MANUAL_INVOCATION, "yo"), callback);

        // Wait for the action to finish running
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // Check that we have a message in the looper's queue
        assertEquals(1, looper.getScheduler().size());
        assertFalse("Callback should not be called yet", callback.onFinishCalled);

        // Run all messages
        looper.runToEndOfTasks();
        assertEquals(0, looper.getScheduler().size());
        assertTrue("Callback on finish is not being called", callback.onFinishCalled);
    }

    /**
     * Test running an action synchronously
     */
    @Test
    public void testRunActionSync() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        actionRunner = new ActionRunner(actionRegistry, executor);

        TestAction action = new TestAction();

        // Register the action
        actionRegistry.registerAction(action, "action!");

        // Run the action by name
        ActionResult result = actionRunner.runActionSync("action!", new ActionArguments(Situation.MANUAL_INVOCATION, "val"));
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Wrong action name", "action!", action.actionName);
        assertEquals("Result status should be COMPLETED", ActionResult.Status.COMPLETED, result.getStatus());

        action = new TestAction();
        result = actionRunner.runActionSync(action, new ActionArguments(Situation.MANUAL_INVOCATION, "val"));
        assertTrue("Action failed to run", action.performCalled);
        assertEquals("Result status should be COMPLETED", ActionResult.Status.COMPLETED, result.getStatus());
    }

    private class TestActionCompletionCallback implements ActionCompletionCallback {

        public ActionResult lastResult;
        public boolean onFinishCalled = false;

        @Override
        public void onFinish(ActionResult result) {
            lastResult = result;
            onFinishCalled = true;
        }
    }
}
