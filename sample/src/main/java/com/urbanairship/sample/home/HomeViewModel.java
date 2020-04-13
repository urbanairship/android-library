/* Copyright Airship and Contributors */

package com.urbanairship.sample.home;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannelListener;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * View model for the HomeFragment.
 */
public class HomeViewModel extends AndroidViewModel {

    private MutableLiveData<String> channelId = new MutableLiveData<>();

    private final AirshipChannelListener channelListener = new AirshipChannelListener() {
        @Override
        public void onChannelCreated(@NonNull String channelId) {
            new Handler(Looper.getMainLooper()).post(() -> refreshChannel());
        }

        @Override
        public void onChannelUpdated(@NonNull String channelId) {
            new Handler(Looper.getMainLooper()).post(() -> refreshChannel());
        }
    };

    public HomeViewModel(Application application) {
        super(application);

        UAirship.shared().getChannel().addChannelListener(channelListener);
        refreshChannel();
    }

    private void refreshChannel() {
        channelId.setValue(UAirship.shared().getChannel().getId());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        UAirship.shared().getChannel().removeChannelListener(channelListener);
    }

    /**
     * Gets the channel Id live data.
     *
     * @return The channel Id live data.
     */
    public LiveData<String> getChannelId() {
        return channelId;
    }

}
