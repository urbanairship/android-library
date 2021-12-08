/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;

import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.LooperMode;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static android.os.Looper.getMainLooper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class AdapterWrapperTest {

    private DisplayCoordinator mockCoordinator;
    private InAppMessageAdapter mockAdapter;

    private AdapterWrapper adapterWrapper;
    private Context context;

    @Before
    public void setup() {
        mockAdapter = mock(InAppMessageAdapter.class);
        mockCoordinator = mock(DisplayCoordinator.class);

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .addAction("action_name", JsonValue.wrap("action_value"))
                                           .build();

        adapterWrapper = new AdapterWrapper("schedule id", JsonValue.wrap("campaigns"),  JsonValue.wrap("reporting"), message, mockAdapter, mockCoordinator);

        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testPrepare() {
        Assets mockAssets = mock(Assets.class);

        when(mockAdapter.onPrepare(context, mockAssets)).thenReturn(InAppMessageAdapter.CANCEL);
        int result = adapterWrapper.prepare(context, mockAssets);

        assertEquals(InAppMessageAdapter.CANCEL, result);
    }

    @Test
    public void testPrepareException() {
        Assets mockAssets = mock(Assets.class);

        when(mockAdapter.onPrepare(context, mockAssets)).thenThrow(new RuntimeException("neat"));
        int result = adapterWrapper.prepare(context, mockAssets);
        assertEquals(InAppMessageAdapter.RETRY, result);
    }

    @Test
    public void testIsReady() {
        assertFalse(adapterWrapper.isReady(context));

        when(mockAdapter.isReady(context)).thenReturn(true);
        assertFalse(adapterWrapper.isReady(context));

        when(mockCoordinator.isReady()).thenReturn(true);
        assertTrue(adapterWrapper.isReady(context));

        when(mockAdapter.isReady(context)).thenReturn(false);
        assertFalse(adapterWrapper.isReady(context));
    }

    @Test
    public void testIsReadyAdapterException() {
        when(mockAdapter.isReady(context)).thenThrow(new RuntimeException("neat"));
        assertFalse(adapterWrapper.isReady(context));
    }

    @Test
    public void testIsReadyCoordinatorException() {
        when(mockCoordinator.isReady()).thenThrow(new RuntimeException("neat"));
        assertFalse(adapterWrapper.isReady(context));
    }

    @Test
    public void testDisplay() throws AdapterWrapper.DisplayException {
        adapterWrapper.display(context);
        verify(mockAdapter).onDisplay(eq(context), any(DisplayHandler.class));
        verify(mockCoordinator).onDisplayStarted(adapterWrapper.message);
    }

    @Test(expected = AdapterWrapper.DisplayException.class)
    public void testDisplayAdapterException() throws AdapterWrapper.DisplayException {
        doThrow(new RuntimeException("neat"))
                .when(mockAdapter).onDisplay(any(Context.class), any(DisplayHandler.class));
        adapterWrapper.display(context);
    }

    @Test(expected = AdapterWrapper.DisplayException.class)
    public void testDisplayCoordinatorException() throws AdapterWrapper.DisplayException {
        doThrow(new RuntimeException("neat"))
                .when(mockCoordinator).onDisplayStarted(adapterWrapper.message);
        adapterWrapper.display(context);
    }

    @Test
    public void testDisplayFinished() {
        adapterWrapper.displayFinished();
        verify(mockCoordinator).onDisplayFinished(adapterWrapper.message);
    }

    @Test
    public void testAdapterFinished() {
        adapterWrapper.adapterFinished(context);
        verify(mockAdapter).onFinish(context);
    }

    /**
     * Tests that if you call displayFinished in the adapter's
     * onDisplay method it will call the display coordinator
     * onDisplayStarted and onDisplayFinished in the proper order.
     */
    @Test
    @LooperMode(PAUSED)
    public void testDisplayFinishedInAdapterOnDisplay() throws AdapterWrapper.DisplayException {
        doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                adapterWrapper.displayFinished();
                return null;
            }
        }).when(mockAdapter).onDisplay(any(Context.class), any(DisplayHandler.class));

        InOrder inOrder = inOrder(mockCoordinator);
        adapterWrapper.display(context);

        shadowOf(getMainLooper()).idle();

        inOrder.verify(mockCoordinator).onDisplayStarted(adapterWrapper.message);
        inOrder.verify(mockCoordinator).onDisplayFinished(adapterWrapper.message);
    }

}
