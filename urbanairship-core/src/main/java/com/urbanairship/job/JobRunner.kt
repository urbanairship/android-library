package com.urbanairship.job

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipExecutors
import com.urbanairship.UALog
import com.urbanairship.Airship
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface JobRunner {

    public fun run(jobInfo: JobInfo, resultConsumer: Consumer<JobResult>)

    public class DefaultRunner(
        private val executor: Executor = AirshipExecutors.newSerialExecutor()
    ) : JobRunner {

        override fun run(jobInfo: JobInfo, resultConsumer: Consumer<JobResult>) {
            executor.execute {
                val airship = Airship.waitForTakeOff(AIRSHIP_WAIT_TIME.inWholeMilliseconds) ?: run {
                    UALog.e("Airship not ready. Rescheduling job: $jobInfo")
                    resultConsumer.accept(JobResult.RETRY)
                    return@execute
                }

                val component = findAirshipComponent(airship, jobInfo.airshipComponentName) ?: run {
                    UALog.e("Unavailable to find airship components for jobInfo: $jobInfo")
                    resultConsumer.accept(JobResult.SUCCESS)
                    return@execute
                }

                component.getJobExecutor(jobInfo).execute {
                    val result = component.onPerformJob(airship, jobInfo)
                    UALog.v("Finished: $jobInfo with result: $result")
                    resultConsumer.accept(result)
                }
            }
        }

        /**
         * Finds the [AirshipComponent]s for a given job.
         *
         * @param componentClassName The component's class name.
         * @param airship The airship instance.
         * @return The airship component.
         */
        private fun findAirshipComponent(
            airship: Airship,
            componentClassName: String
        ): AirshipComponent? {
            if (componentClassName.isEmpty()) {
                return null
            }

            return airship.getComponentsList().firstOrNull { it.javaClass.name == componentClassName }
        }

        private companion object {
            private val AIRSHIP_WAIT_TIME = 5.seconds
        }
    }
}
