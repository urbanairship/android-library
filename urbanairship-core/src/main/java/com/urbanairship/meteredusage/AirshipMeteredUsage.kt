/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.http.RequestResult
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.remoteconfig.MeteredUsageConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/**
 * Airship Metered Usage tracker.
 *
 * @hide
 */
@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipMeteredUsage @JvmOverloads internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val store: EventsDao = EventsDatabase.persistent(context).eventsDao(),
    private val client: MeteredUsageApiClient = MeteredUsageApiClient(config),
    private val contact: Contact,
    private val jobDispatcher: JobDispatcher = JobDispatcher.shared(context),
) : AirshipComponent(context, dataStore) {

    internal companion object {
        private const val WORK_ID = "MeteredUsage.upload"
        private const val RATE_LIMIT_ID = "MeteredUsage.rateLimit"
    }

    private val usageConfig: AtomicReference<MeteredUsageConfig> = AtomicReference(MeteredUsageConfig.DEFAULT)

    init {
        jobDispatcher.setRateLimit(RATE_LIMIT_ID, 1, MeteredUsageConfig.DEFAULT.intervalMs, TimeUnit.MILLISECONDS)

        config.addConfigListener {
            updateConfig()
        }
        updateConfig()
    }

    private fun updateConfig() {
        val config = config.remoteConfig.meteredUsageConfig ?: MeteredUsageConfig.DEFAULT
        val old = this.usageConfig.getAndSet(config)
        if (old == config) {
            return
        }

        jobDispatcher.setRateLimit(RATE_LIMIT_ID, 1, config.intervalMs, TimeUnit.MILLISECONDS)

        if (!old.isEnabled && config.isEnabled) {
            scheduleUpload(config.initialDelayMs)
        }
    }

    private fun scheduleUpload(delay: Long) {
        if (!usageConfig.get().isEnabled) {
            return
        }

        jobDispatcher.dispatch(JobInfo.newBuilder()
            .setAirshipComponent(AirshipMeteredUsage::class.java)
            .setAction(WORK_ID)
            .setConflictStrategy(JobInfo.KEEP)
            .setNetworkAccessRequired(true)
            .setMinDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        )
    }

    @WorkerThread
    public suspend fun addEvent(event: MeteredUsageEventEntity) {
        if (!usageConfig.get().isEnabled) {
            return
        }

        var eventToStore = event
        if (privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            if (eventToStore.contactId == null) {
                eventToStore = event.copyWithContactId(contact.stableContactInfo().contactId)
            }
        } else {
            eventToStore = event.withAnalyticsDisabled()
        }

        store.addEvent(eventToStore)
        scheduleUpload(delay = 0)
    }

    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        if (!usageConfig.get().isEnabled) {
            UALog.v { "Config disabled, skipping upload." }
            return JobResult.SUCCESS
        }

        var events = store.getAllEvents()
        if (events.isEmpty()) {
            UALog.v { "No events, skipping upload." }
            return JobResult.SUCCESS
        }

        var channelId = airship.channel.id
        if (!privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            channelId = null
            events = events.map { it.withAnalyticsDisabled() }
        }

        UALog.v { "Uploading events" }

        val result = runBlocking {
            try {
                client.uploadEvents(events, channelId)
            } catch (ex: Exception) {
                return@runBlocking RequestResult(ex)
            }
        }

        if (!result.isSuccessful) {
            UALog.v { "Uploading failed" }
            return JobResult.FAILURE
        }

        UALog.v { "Uploading success" }

        store.deleteAll(events.map { it.eventId }.toList())

        return JobResult.SUCCESS
    }
}
