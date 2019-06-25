package com.urbanairship;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.util.AirshipThreadFactory;
import com.urbanairship.util.SerialExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    public static final ExecutorService THREAD_POOL_EXECUTOR;

    static {
        int processors = Runtime.getRuntime().availableProcessors();
        int minThreads = 2;
        int maxThreads = processors * 2;
        int keepAliveTime = 30;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(minThreads, maxThreads, keepAliveTime,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), AirshipThreadFactory.DEFAULT_THREAD_FACTORY);
        executor.allowCoreThreadTimeOut(true);

        THREAD_POOL_EXECUTOR = executor;
    }

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
