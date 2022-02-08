package com.urbanairship;

import com.google.common.util.concurrent.MoreExecutors;

import org.robolectric.android.util.concurrent.RoboExecutorService;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Implements(AirshipExecutors.class)
public class ShadowAirshipExecutorsLegacy {
    private static final ExecutorService EXECUTOR = new RoboExecutorService();

    @Implementation
    public static ExecutorService threadPoolExecutor() {
        return EXECUTOR;
    }

    @Implementation
    public static Executor newSerialExecutor() {
        return EXECUTOR;
    }
}
