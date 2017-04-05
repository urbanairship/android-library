/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UALocationProviderTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    Context context;
    UALocationProvider provider;

    LocationAdapter mockAdapterOne;
    LocationAdapter mockAdapterTwo;
    LocationRequestOptions options;
    LocationCallback locationCallback;

    @Before
    public void setUp() {
        context = TestApplication.getApplication();

        locationCallback = new LocationCallback() {
            @Override
            public void onResult(Location location) {

            }
        };

        mockAdapterOne = mock(LocationAdapter.class);
        mockAdapterTwo = mock(LocationAdapter.class);

        Intent intent = new Intent(context, LocationService.class).setAction(LocationService.ACTION_LOCATION_UPDATE);
        provider = new UALocationProvider(context, intent, mockAdapterOne, mockAdapterTwo);

        options = LocationRequestOptions.createDefaultOptions();
    }


    /**
     * Test onDestroy disconnects from any location adapters
     */
    @Test
    public void testOnDestroy() {
        when(mockAdapterOne.connect(context)).thenReturn(false);
        when(mockAdapterTwo.connect(context)).thenReturn(true);

        // Call any method to force a connection to the adapter
        provider.areUpdatesRequested();

        // Call onDestroy()
        provider.onDestroy();

        // Verify we only disconnected from the connected provider (mockAdapterTwo)
        verify(mockAdapterOne, times(0)).disconnect(context);
        verify(mockAdapterTwo, times(1)).disconnect(context);
    }

    /**
     * Test canceling location updates tries to cancel updates
     * on all of the adapters.
     */
    @Test
    public void testCancelRequests() {
        when(mockAdapterOne.connect(context)).thenReturn(false);
        when(mockAdapterTwo.connect(context)).thenReturn(true);

        // Request options
        provider.requestLocationUpdates(options);

        // Cancel requests
        provider.cancelRequests();

        // Verify we attempted to connect to both adapters
        verify(mockAdapterOne, times(1)).connect(context);
        verify(mockAdapterTwo, times(1)).connect(context);

        // Verify we only canceled requests on the adapter that was connected
        verify(mockAdapterOne, times(0)).cancelLocationUpdates(eq(context), any(PendingIntent.class));
        verify(mockAdapterTwo, times(1)).cancelLocationUpdates(eq(context), any(PendingIntent.class));
    }

    /**
     * Test requesting location updates only requests from the connected
     * adapter.
     */
    @Test
    public void testRequestLocationUpdates() {
        when(mockAdapterTwo.connect(context)).thenReturn(true);

        provider.requestLocationUpdates(options);

        verify(mockAdapterTwo).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));
        verify(mockAdapterOne, times(0)).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));
    }

    /**
     * Test single location updates only requests from the connected adapter.
     */
    @Test
    public void testSingleLocationRequest() {
        PendingResult<Location> request = new PendingResult<Location>(locationCallback) {
            @Override
            protected void onCancel() {
            }
        };

        when(mockAdapterOne.connect(context)).thenReturn(true);
        when(mockAdapterOne.requestSingleLocation(context, locationCallback, options)).thenReturn(request);

        assertEquals("Should return the adapters pending result.", provider.requestSingleLocation(locationCallback, options), request);
    }


    /**
     * Test single request returns null if none of the adapters were connectible.
     */
    @Test
    public void testSingleLocationRequestNoAdapter() {
        when(mockAdapterOne.connect(context)).thenReturn(false);

        assertNull("Should return null if no connected adapter", provider.requestSingleLocation(locationCallback, options));
    }


    /**
     * Test single request returns null if the adapter throws a security exception.
     */
    @Test
    public void testSingleLocationNoPermissions() {
        when(mockAdapterOne.connect(context)).thenReturn(true);
        when(mockAdapterOne.requestSingleLocation(context, locationCallback, options)).thenThrow(new SecurityException("Nope"));

        assertNull("Should return null if the adapter throws a security exception.", provider.requestSingleLocation(locationCallback, options));
    }

    /**
     * Test requesting location updates handles security exceptions.
     */
    @Test
    public void testRequestLocationUpdatesNoPermissions() {
        when(mockAdapterOne.connect(context)).thenReturn(true);
        doThrow(new SecurityException("Nope")).when(mockAdapterOne).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));

        provider.requestLocationUpdates(options);
    }

    /**
     * Test canceling location updates handles security exceptions.
     */
    @Test
    public void testCancelLocationUpdatesNoPermissions() {
        when(mockAdapterOne.connect(context)).thenReturn(true);
        doThrow(new SecurityException("Nope")).when(mockAdapterOne).cancelLocationUpdates(eq(context), any(PendingIntent.class));

        provider.cancelRequests();
    }

}
