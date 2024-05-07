package com.urbanairship.automation

import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import com.urbanairship.iam.InAppMessagingInterface
import com.urbanairship.iam.legacy.LegacyInAppMessagingInterface
import com.urbanairship.automation.remotedata.AutomationRemoteDataSubscriber
import com.urbanairship.config.AirshipRuntimeConfig

/**
 * Provides a control interface for creating, canceling and executing in-app automations.
 */
public class InAppAutomation
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal constructor(
    private val engine: AutomationEngine,
    public val inAppMessaging: InAppMessagingInterface,
    public val legacyInAppMessaging: LegacyInAppMessagingInterface,
    private val remoteDataSubscriber: AutomationRemoteDataSubscriber,
    private val dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val config: AirshipRuntimeConfig
) {

    private val subscriptions = mutableListOf<() -> Unit>()

    /** Paused state of in-app automation. */
    @Suppress("MemberVisibilityCanBePrivate") // Public API
    public var isPaused: Boolean
        get() {
            synchronized(this) {
                return dataStore.getBoolean(PAUSED_STORE_KEY, config.configOptions.autoPauseInAppAutomationOnLaunch)
            }
        }

        set(value) {
            synchronized(this) {
                dataStore.put(PAUSED_STORE_KEY, value)
                engine.setEnginePaused(value)
            }
        }

    /**
     * Creates the provided schedules or updates them if they already exist.
     * @param schedules: The schedules to create or update.
     */
    public suspend fun upsertSchedules(schedules: List<AutomationSchedule>) {
        engine.upsertSchedules(schedules)
    }

    /**
     * Cancels an in-app automation via its schedule identifier.
     * @param identifier: The schedule identifier to cancel.
     */
    public suspend fun cancelSchedule(identifier: String) {
        engine.cancelSchedules(listOf(identifier))
    }

    /**
     * Cancels multiple in-app automations via their schedule identifiers.
     * @param identifiers: The schedule identifiers to cancel.
     */
    public suspend fun cancelSchedules(identifiers: List<String>) {
        engine.cancelSchedules(identifiers)
    }

    /**
     * Cancels multiple in-app automations via their group.
     * @param group: The group to cancel.
     */
    public suspend fun cancelSchedules(group: String) {
        engine.cancelSchedules(group)
    }

    internal suspend fun cancelSchedulesWith(type: AutomationSchedule.ScheduleType) {
        engine.cancelSchedulesWith(type)
    }

    /**
     * Gets the in-app automation with the provided schedule identifier.
     * @param identifier: The schedule identifier.
     * @return The in-app automation corresponding to the provided schedule identifier.
     */
    public suspend fun getSchedules(identifier: String): AutomationSchedule? {
        return engine.getSchedule(identifier)
    }

    /**
     * Gets the in-app automation with the provided group.
     * @param group: The group to get.
     * @return The in-app automation corresponding to the provided group.
     */
    public suspend fun getSchedulesForGroup(group: String): List<AutomationSchedule> {
        return engine.getSchedules(group)
    }

    internal fun airshipReady() {
        engine.setExecutionPaused(isPaused)
        engine.start()

        val listener = PrivacyManager.Listener { privacyManagerUpdated() }
        privacyManager.addListener(listener)

        subscriptions.add { privacyManager.removeListener(listener) }

        privacyManagerUpdated()
    }

    internal fun tearDown() {
        subscriptions.forEach { it.invoke() }
    }

    private fun privacyManagerUpdated() {
        if (privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION)) {
            engine.setEnginePaused(false)
            remoteDataSubscriber.subscribe()
        } else {
            engine.setEnginePaused(true)
            remoteDataSubscriber.unsubscribe()
        }
    }

    public companion object {
        private const val PAUSED_STORE_KEY = "com.urbanairship.iam.paused"

        /**
         * The shared InAppAutomation instance.
         *
         * `Airship.takeOff` must be called before accessing this instance.
         */
        @JvmStatic
        public fun shared(): InAppAutomation {
            return UAirship.shared().requireComponent(InAppAutomationComponent::class.java).automation
        }
    }
}
