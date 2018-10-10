package com.urbanairship.iam.banner;

import android.support.annotation.NonNull;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * BannerAdapter factory.
 */
public class BannerAdapterFactory implements InAppMessageAdapter.Factory {

    @NonNull
    @Override
    public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
        return BannerAdapter.newAdapter(message);
    }
}
