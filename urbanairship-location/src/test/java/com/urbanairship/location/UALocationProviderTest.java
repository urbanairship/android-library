/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.urbanairship.ResultCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class UALocationProviderTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    Context context;
    UALocationProvider provider;

    LocationAdapter mockAdapterOne;
    LocationAdapter mockAdapterTwo;
    LocationRequestOptions options;
    ResultCallback<Location> locationCallback;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        locationCallback = new ResultCallback<Location>() {
            @Override
            public void onResult(Location location) {

            }
        };

        mockAdapterOne = mock(LocationAdapter.class);
        mockAdapterTwo = mock(LocationAdapter.class);

        Intent intent = new Intent(context, LocationReceiver.class).setAction(LocationReceiver.ACTION_LOCATION_UPDATE);
        provider = new UALocationProvider(context, intent, mockAdapterOne, mockAdapterTwo);

        options = LocationRequestOptions.createDefaultOptions();
    }

    /**
     * Test canceling location updates tries to cancel updates
     * on all of the adapters.
     */
    @Test
    public void testCancelRequests() {
        when(mockAdapterOne.isAvailable(context)).thenReturn(false);
        when(mockAdapterTwo.isAvailable(context)).thenReturn(true);

        // Request options
        provider.requestLocationUpdates(options);

        // Cancel requests
        provider.cancelRequests();

        // Verify we attempted to check both adapters
        verify(mockAdapterOne, times(1)).isAvailable(context);
        verify(mockAdapterTwo, times(1)).isAvailable(context);

        // Verify we only canceled requests on the adapter that are available
        verify(mockAdapterOne, times(0)).cancelLocationUpdates(eq(context), any(PendingIntent.class));
        verify(mockAdapterTwo, times(1)).cancelLocationUpdates(eq(context), any(PendingIntent.class));
    }

    /**
     * Test requesting location updates only requests from the first available
     * adapter.
     */
    @Test
    public void testRequestLocationUpdates() {
        when(mockAdapterTwo.isAvailable(context)).thenReturn(true);

        provider.requestLocationUpdates(options);

        verify(mockAdapterTwo).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));
        verify(mockAdapterOne, times(0)).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));
    }

    /**
     * Test single location updates only requests from the first available adapter.
     */
    @Test
    public void testSingleLocationRequest() {
        when(mockAdapterTwo.isAvailable(context)).thenReturn(true);

        provider.requestSingleLocation(options, locationCallback);
        verify(mockAdapterTwo).requestSingleLocation(eq(context), eq(options), eq(locationCallback));
        verify(mockAdapterOne, times(0)).requestSingleLocation(eq(context), eq(options), eq(locationCallback));
    }

    /**
     * Test single request does not exception out when the adapter throws security exceptions.
     */
    @Test
    public void testSingleLocationNoPermissions() {
        when(mockAdapterOne.isAvailable(context)).thenReturn(true);
        doThrow(new SecurityException("Nope")).when(mockAdapterOne).requestSingleLocation(context, options, locationCallback);
    }

    /**
     * Test requesting location updates handles security exceptions.
     */
    @Test
    public void testRequestLocationUpdatesNoPermissions() {
        when(mockAdapterOne.isAvailable(context)).thenReturn(true);
        doThrow(new SecurityException("Nope")).when(mockAdapterOne).requestLocationUpdates(eq(context), eq(options), any(PendingIntent.class));

        provider.requestLocationUpdates(options);
    }

    /**
     * Test canceling location updates handles security exceptions.
     */
    @Test
    public void testCancelLocationUpdatesNoPermissions() {
        when(mockAdapterOne.isAvailable(context)).thenReturn(true);
        doThrow(new SecurityException("Nope")).when(mockAdapterOne).cancelLocationUpdates(eq(context), any(PendingIntent.class));

        provider.cancelRequests();
    }

}
