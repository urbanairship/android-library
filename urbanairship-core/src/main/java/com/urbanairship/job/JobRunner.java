package com.urbanairship.job;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

@VisibleForTesting
interface JobRunner {
    void run(@NonNull JobInfo jobInfo, @NonNull Consumer<JobResult> resultConsumer);

    class DefaultRunner implements JobRunner {
        private final Executor executor = AirshipExecutors.newSerialExecutor();
        private static final long AIRSHIP_WAIT_TIME_MS = 5000; // 5 seconds.

        @Override
        public void run(@NonNull JobInfo jobInfo, @NonNull Consumer<JobResult> resultConsumer) {
            executor.execute(() -> {
                final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
                if (airship == null) {
                    Logger.error("UAirship not ready. Rescheduling job: %s", jobInfo);
                    resultConsumer.accept(JobResult.RETRY);
                    return;
                }

                final AirshipComponent component = findAirshipComponent(airship, jobInfo.getAirshipComponentName());
                if (component == null) {
                    Logger.error("Unavailable to find airship components for jobInfo: %s", jobInfo);
                    resultConsumer.accept(JobResult.SUCCESS);
                    return;
                }

                if (!component.isComponentEnabled()) {
                    Logger.debug("Component disabled. Dropping jobInfo: %s", jobInfo);
                    resultConsumer.accept(JobResult.SUCCESS);
                    return;
                }

                component.getJobExecutor(jobInfo).execute(() -> {
                    JobResult result = component.onPerformJob(airship, jobInfo);
                    Logger.verbose("Finished: %s with result: %s", jobInfo, result);
                    resultConsumer.accept(result);
                });
            });
        }

        /**
         * Finds the {@link AirshipComponent}s for a given job.
         *
         * @param componentClassName The component's class name.
         * @param airship The airship instance.
         * @return The airship component.
         */
        private AirshipComponent findAirshipComponent(@NonNull UAirship airship, String componentClassName) {
            if (UAStringUtil.isEmpty(componentClassName)) {
                return null;
            }

            for (final AirshipComponent component : airship.getComponents()) {
                if (component.getClass().getName().equals(componentClassName)) {
                    return component;
                }
            }

            return null;
        }
    }
}
