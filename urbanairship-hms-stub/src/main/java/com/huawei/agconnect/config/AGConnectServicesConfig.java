package com.huawei.agconnect.config;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AGConnectServicesConfig {

    @NonNull
    public static AGConnectServicesConfig fromContext(@NonNull Context context) {
        return new AGConnectServicesConfig();
    }

    @Nullable
    public String getString(@NonNull String key) {
        return null;
    }
}
