package com.huawei.hms.push;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HmsMessageService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onMessageReceived(@NonNull RemoteMessage message) {

    }

    public void onNewToken(@Nullable String string) {

    }
}
