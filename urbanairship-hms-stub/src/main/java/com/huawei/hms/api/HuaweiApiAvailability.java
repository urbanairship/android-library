package com.huawei.hms.api;

import android.content.Context;

import androidx.annotation.NonNull;

public class HuaweiApiAvailability {

    @NonNull
    public static HuaweiApiAvailability getInstance() {
        return new HuaweiApiAvailability();
    }

    public int isHuaweiMobileNoticeAvailable(@NonNull Context context) {
        return -1;
    }
}
