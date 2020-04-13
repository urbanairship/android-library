package com.urbanairship.iam.banner;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

import androidx.annotation.NonNull;

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
