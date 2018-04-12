package com.urbanairship.iam.banner;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * BannerAdapter factory.
 */
public class BannerAdapterFactory implements InAppMessageAdapter.Factory {

    @Override
    public InAppMessageAdapter createAdapter(InAppMessage message) {
        return BannerAdapter.newAdapter(message);
    }
}
