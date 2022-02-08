package com.urbanairship;

import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.android.util.concurrent.RoboExecutorService;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Implements(AirshipExecutors.class)
public class ShadowAirshipExecutorsPaused {
    @SuppressWarnings("UnstableApiUsage")
    private static final ExecutorService EXECUTOR =
            new PausedExecutorService();

    @Implementation
    public static ExecutorService threadPoolExecutor() {
        return EXECUTOR;
    }

    @Implementation
    public static Executor newSerialExecutor() {
        return EXECUTOR;
    }
}
