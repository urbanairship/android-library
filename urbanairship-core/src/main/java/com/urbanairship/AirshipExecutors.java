package com.urbanairship;

import com.urbanairship.util.AirshipThreadFactory;
import com.urbanairship.util.SerialExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Common Executors for Airship.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipExecutors {

    /**
     * The shared thread pool executor.
     */
    @NonNull
    public static final ExecutorService THREAD_POOL_EXECUTOR = Executors.newCachedThreadPool(AirshipThreadFactory.DEFAULT_THREAD_FACTORY);

    /**
     * Creates a new serial executor that shares threads with the {@link #THREAD_POOL_EXECUTOR}.
     *
     * @return A new serial executor.
     */
    @NonNull
    public static Executor newSerialExecutor() {
        return new SerialExecutor(THREAD_POOL_EXECUTOR);
    }

}
