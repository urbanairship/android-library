/* Copyright Airship and Contributors */

package com.urbanairship.sample.home;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.push.RegistrationListener;

/**
 * View model for the HomeFragment.
 */
public class HomeViewModel extends AndroidViewModel {

    private MutableLiveData<String> channelId = new MutableLiveData<>();

    private final RegistrationListener registrationListener = new RegistrationListener() {
        @Override
        public void onChannelCreated(@NonNull String channelId) {
            new Handler(Looper.getMainLooper()).post(() -> refreshChannel());
        }

        @Override
        public void onChannelUpdated(@NonNull String channelId) {
            new Handler(Looper.getMainLooper()).post(() -> refreshChannel());
        }

        @Override
        public void onPushTokenUpdated(@NonNull String token) {}
    };

    public HomeViewModel(Application application) {
        super(application);

        UAirship.shared().getPushManager().addRegistrationListener(registrationListener);
        refreshChannel();
    }

    private void refreshChannel() {
        channelId.setValue(UAirship.shared().getPushManager().getChannelId());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        UAirship.shared().getPushManager().removeRegistrationListener(registrationListener);
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
