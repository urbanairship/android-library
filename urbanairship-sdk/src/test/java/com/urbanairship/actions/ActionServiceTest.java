/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.CustomShadowService;
import com.urbanairship.UAirship;

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

import java.util.HashMap;
import java.util.Map;

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
     * Test that the ActionService.runActions starts the
     * action service with the correct intent.
     */
    @Test
    public void testRunActionsWithString() {
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        String actionsPayload = "{ \"actionName\": \"actionValue\" }";

        Bundle metadata = new Bundle();
        metadata.putString("oh", "hi");

        ActionService.runActions(context, actionsPayload, Action.SITUATION_WEB_VIEW_INVOCATION, metadata);

        Intent runActionsIntent = shadowApplication.getNextStartedService();
        assertNotNull(runActionsIntent);

        assertEquals("Should add an intent with action RUN_ACTIONS_ACTION",
                runActionsIntent.getAction(), ActionService.ACTION_RUN_ACTIONS);

        Bundle actionBundle = runActionsIntent.getBundleExtra(ActionService.EXTRA_ACTIONS_BUNDLE);
        assertEquals(actionBundle.getParcelable("actionName"), ActionValue.wrap("actionValue"));

        assertEquals("Should add the situation", Action.SITUATION_WEB_VIEW_INVOCATION,
                runActionsIntent.getSerializableExtra(ActionService.EXTRA_SITUATION));
    }

    /**
     * Test that the ActionService.runActions starts the
     * action service with the correct intent.
     */
    @Test
    public void testRunActions() {
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        Map<String, ActionValue> actions = new HashMap<>();
        actions.put("actionName", ActionValue.wrap("actionValue"));

        Bundle metadata = new Bundle();
        metadata.putString("oh", "hi");

        ActionService.runActions(context, actions, Action.SITUATION_PUSH_OPENED, metadata);

        Intent runActionsIntent = shadowApplication.getNextStartedService();
        assertNotNull(runActionsIntent);

        assertEquals("Should add an intent with action RUN_ACTIONS_ACTION",
                runActionsIntent.getAction(), ActionService.ACTION_RUN_ACTIONS);

        Bundle actionBundle = runActionsIntent.getBundleExtra(ActionService.EXTRA_ACTIONS_BUNDLE);
        assertEquals(actionBundle.getParcelable("actionName"), ActionValue.wrap("actionValue"));

        assertEquals("Should add the situation", Action.SITUATION_PUSH_OPENED,
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
        ActionService.runActions(context, actionsPayload, Action.SITUATION_WEB_VIEW_INVOCATION, null);

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
    public void testHandleRunActionIntent() {

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        CustomShadowService shadowService = (CustomShadowService) Shadows.shadowOf(service);
        shadowApplication.clearStartedServices();

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

        // Metadata
        Bundle metadata = new Bundle();
        metadata.putString("oh", "hi");

        // Create the intent
        ActionService.runActions(context, "{ \"actionName\": \"actionValue\" }", Action.SITUATION_WEB_VIEW_INVOCATION, metadata);
        Intent runActionsIntent = shadowApplication.getNextStartedService();

        // Start the service
        service.onStartCommand(runActionsIntent, 0, 1);

        verify(runRequest).setValue(ActionValue.wrap("actionValue"));
        verify(runRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(runRequest).run(Mockito.any(ActionCompletionCallback.class));
        verify(runRequest).setMetadata(argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Bundle bundle) {
                return bundle.getString("oh").equals("hi");
            }
        }));

        // Verify that the service called stop self with the last start id
        assertEquals(1, shadowService.getLastStopSelfId());
    }
}
