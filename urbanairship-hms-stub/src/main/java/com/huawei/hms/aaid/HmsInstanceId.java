package com.huawei.hms.aaid;

import android.content.Context;
import android.media.MediaSession2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HmsInstanceId {

    @NonNull
    public static HmsInstanceId getInstance(@NonNull Context context) {
        return new HmsInstanceId();
    }

    @Nullable
    public String getToken(@NonNull String appId, @NonNull String hcmScope) {
        return null;
    }

}
