package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.CustomShadowService;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActionServiceTest extends BaseTestCase {

    ActionService service;
    ActionRunRequestFactory actionRunRequestFactory;
    private Context context = UAirship.getApplicationContext();

    @Before
    public void setUp() {
        actionRunRequestFactory = Mockito.mock(ActionRunRequestFactory.class);
        service = new ActionService(actionRunRequestFactory);
    }

    /**
     * Test that the ActionService.runActionsPayload starts the
     * action service with the correct intent.
     */
    @Test
    public void testRunActionsPayload() {
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        String actionsPayload = "{ \"actionName\": \"actionValue\" }";
        Bundle extras = new Bundle();
        extras.putString("com.urbanairship.actions", actionsPayload);

        ActionService.runActionsPayload(context, actionsPayload, Situation.WEB_VIEW_INVOCATION);

        Intent runActionsIntent = shadowApplication.getNextStartedService();
        assertNotNull(runActionsIntent);

        assertEquals("Should add an intent with action RUN_ACTIONS_ACTION",
                runActionsIntent.getAction(), ActionService.ACTION_RUN_ACTIONS);

        assertEquals("Should add the actions extra", actionsPayload,
                runActionsIntent.getStringExtra(ActionService.EXTRA_ACTIONS_PAYLOAD));

        assertEquals("Should add the situation", Situation.WEB_VIEW_INVOCATION,
                runActionsIntent.getSerializableExtra(ActionService.EXTRA_SITUATION));
    }


    /**
     * Test that the ActionService.runActionsPayload does not start
     * the service if the actions payload is null or empty
     */
    @Test
    public void testRunActionsPayloadInvalid() {
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        String actionsPayload = null;
        ActionService.runActionsPayload(context, actionsPayload, Situation.WEB_VIEW_INVOCATION);

        Intent runActionsIntent = shadowApplication.getNextStartedService();
        assertNull("Action service should not start with a null actions payload",
                runActionsIntent);

        Bundle extras = new Bundle();
        extras.putString("com.urbanairship.actions", "");
        runActionsIntent = shadowApplication.getNextStartedService();
        assertNull("Actions service should not start if the actions payload is empty",
                runActionsIntent);
    }

    /**
     * Test running actions in the action service actually runs the actions
     * and calls stop self once its done.
     */
    @Test
    @Config(shadows = { CustomShadowService.class })
    public void testRunActions() {

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        CustomShadowService shadowService = (CustomShadowService) Shadows.shadowOf(service);
        shadowApplication.clearStartedServices();

        Situation situation = Situation.PUSH_RECEIVED;
        String actionsPayload = "{ \"actionName\": \"actionValue\" }";

        // Create the intent that starts the service
        Intent intent = new Intent(ActionService.ACTION_RUN_ACTIONS);
        intent.putExtra(ActionService.EXTRA_ACTIONS_PAYLOAD, actionsPayload);
        intent.putExtra(ActionService.EXTRA_SITUATION, situation);


        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the request finish callback
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(null, ActionResult.newEmptyResult());
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));


        // Start the service
        service.onStartCommand(intent, 0, 1);

        verify(runRequest).setValue("actionValue");
        verify(runRequest).setSituation(situation);
        verify(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        // Verify that the service called stop self with the last start id
        assertEquals(1, shadowService.getLastStopSelfId());
    }

    /**
     * Test running actions in the action service actually runs the actions
     * with the correct push message meta data.
     */
    @Test
    @Config(shadows = { CustomShadowService.class })
    public void testRunActionsWithPushMessage() {

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        CustomShadowService shadowService = (CustomShadowService) Shadows.shadowOf(service);
        shadowApplication.clearStartedServices();

        final Situation situation = Situation.PUSH_RECEIVED;
        String actionsPayload = "{ \"actionName\": \"actionValue\" }";
        Bundle actionBundle = new Bundle();
        actionBundle.putString("oh", "hi");

        // Create the intent that starts the service
        Intent intent = new Intent(ActionService.ACTION_RUN_ACTIONS);
        intent.putExtra(ActionService.EXTRA_ACTIONS_PAYLOAD, actionsPayload);
        intent.putExtra(ActionService.EXTRA_SITUATION, situation);
        intent.putExtra(ActionService.EXTRA_PUSH_BUNDLE, actionBundle);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action request callback
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(null, ActionResult.newEmptyResult());
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        // Start the service
        service.onStartCommand(intent, 0, 1);

        verify(runRequest).setValue("actionValue");

        verify(runRequest).setMetadata(argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Object o) {
                Bundle bundle = (Bundle) o;
                PushMessage message = bundle.getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
                return message.getPushBundle().getString("oh").equals("hi");
            }
        }));


        verify(runRequest).setSituation(situation);
        verify(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        // Verify that the service called stop self with the last start id
        assertEquals(1, shadowService.getLastStopSelfId());
    }
}
