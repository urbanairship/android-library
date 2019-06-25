package com.urbanairship.util;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * Executor that executes {@link Runnable} serially on another executor.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SerialExecutor implements Executor {

    private final Executor executor;
    private final ArrayDeque<Runnable> runnables = new ArrayDeque<>();
    private boolean isExecuting = false;

    /**
     * Default constructor.
     *
     * @param executor The executor that performs the runnables.
     */
    public SerialExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(@Nullable final Runnable runnable) {
        if (runnable == null) {
            return;
        }

        Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } finally {
                    next();
                }
            }
        };

        synchronized (runnables) {
            runnables.offer(wrapped);
            if (!isExecuting) {
                next();
            }
        }
    }

    private void next() {
        synchronized (runnables) {
            Runnable next = runnables.pollFirst();
            if (next != null) {
                isExecuting = true;
                executor.execute(next);
            } else {
                isExecuting = false;
            }
        }
    }

}
