/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.home;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.UAirship;
import com.urbanairship.sample.SampleAirshipReceiver;

/**
 * View model for the HomeFragment.
 */
public class HomeViewModel extends AndroidViewModel {

    private MutableLiveData<String> channelId = new MutableLiveData<>();

    private final BroadcastReceiver channelIdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshChannel();
        }
    };

    public HomeViewModel(Application application) {
        super(application);

        // Register a local broadcast manager to listen for ACTION_UPDATE_CHANNEL
        LocalBroadcastManager locationBroadcastManager = LocalBroadcastManager.getInstance(application);

        // Use local broadcast manager to receive registration events to update the channel
        IntentFilter channelIdUpdateFilter;
        channelIdUpdateFilter = new IntentFilter();
        channelIdUpdateFilter.addAction(SampleAirshipReceiver.ACTION_UPDATE_CHANNEL);
        locationBroadcastManager.registerReceiver(channelIdUpdateReceiver, channelIdUpdateFilter);
        refreshChannel();
    }

    private void refreshChannel() {
        channelId.setValue(UAirship.shared().getPushManager().getChannelId());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        LocalBroadcastManager locationBroadcastManager = LocalBroadcastManager.getInstance(getApplication());
        locationBroadcastManager.unregisterReceiver(channelIdUpdateReceiver);
    }

    /**
     * Gets the channel Id live data.
     *
     * @return The channel Id live data.
     */
    public LiveData<String> getChanneId() {
        return channelId;
    }

}
