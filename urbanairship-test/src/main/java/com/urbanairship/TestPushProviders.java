package com.urbanairship;

import com.urbanairship.push.PushProvider;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestPushProviders extends PushProviders {

    public TestPushProviders(@NonNull AirshipConfigOptions config) {
        super(config);
    }

    @Nullable
    @Override
    public PushProvider getProvider(int platform, @NonNull String providerClass) {
        return null;
    }

    @NonNull
    @Override
    public List<PushProvider> getAvailableProviders() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PushProvider getBestProvider(int platform) {
        return null;
    }

    @Nullable
    @Override
    PushProvider getBestProvider() {
        return null;
    }

}
