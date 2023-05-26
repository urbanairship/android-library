/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PushProviders
import com.urbanairship.UAirship
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.base.Supplier
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleChangedListener
import com.urbanairship.locale.LocaleManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Clock
import java.util.Random
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * RemoteData top-level class.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteData @VisibleForTesting internal constructor(
    context: Context,
    private val preferenceDataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val localeManager: LocaleManager,
    private val pushManager: PushManager,
    private val contact: Contact,
    private val providers: List<RemoteDataProvider>,
    private val appVersion: Long,
    private val refreshManager: RemoteDataRefreshManager,
    private val activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    coroutineDispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    private var lastForegroundDispatchTime: Long = 0
    private val changeTokenLock: Lock = ReentrantLock()

    internal fun setContactSourceEnabled(enabled: Boolean) {
        val provider = this.providers.firstOrNull {
            it.source == RemoteDataSource.CONTACT
        } ?: return

        if (provider.isEnabled != enabled) {
            provider.isEnabled = enabled
            dispatchRefreshJob()
        }
    }

    public constructor(
        context: Context,
        config: AirshipRuntimeConfig,
        preferenceDataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        localeManager: LocaleManager,
        pushManager: PushManager,
        pushProviders: Supplier<PushProviders>,
        contact: Contact,
    ) : this(
        context = context,
        preferenceDataStore = preferenceDataStore,
        privacyManager = privacyManager,
        localeManager = localeManager,
        pushManager = pushManager,
        contact = contact,
        providers = createProviders(
            context = context,
            preferenceDataStore = preferenceDataStore,
            config = config,
            pushProviders = pushProviders,
            contact = contact
        ),
        appVersion = UAirship.getAppVersion(),
        refreshManager = RemoteDataRefreshManager(
            JobDispatcher.shared(context),
            privacyManager
        )
    )

    private val applicationListener: ApplicationListener = object : SimpleApplicationListener() {
        override fun onForeground(time: Long) {
            val now = clock.currentTimeMillis()
            if (now >= lastForegroundDispatchTime + foregroundRefreshInterval) {
                updateChangeToken()
                dispatchRefreshJob()
                lastForegroundDispatchTime = now
            }
        }
    }

    private val localeChangedListener = LocaleChangedListener { dispatchRefreshJob() }
    private val pushListener = PushListener { message: PushMessage, _ ->
        if (message.isRemoteDataUpdate) {
            updateChangeToken()
            dispatchRefreshJob()
        }
    }

    private val privacyListener = PrivacyManager.Listener { dispatchRefreshJob() }

    init {
        activityMonitor.addApplicationListener(applicationListener)
        pushManager.addInternalPushListener(pushListener)
        localeManager.addListener(localeChangedListener)
        privacyManager.addListener(privacyListener)

        scope.launch {
            contact.contactIdUpdateFlow.mapNotNull { it?.contactId }.distinctUntilChanged().collect {
                dispatchRefreshJob()
            }
        }

        refreshManager.dispatchRefreshJob()
    }

    public override fun tearDown() {
        pushManager.removePushListener(pushListener)
        activityMonitor.removeApplicationListener(applicationListener)
        localeManager.removeListener(localeChangedListener)
        privacyManager.removeListener(privacyListener)
    }

    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        if (!privacyManager.isAnyFeatureEnabled) {
            return JobResult.SUCCESS
        }
        if (ACTION_REFRESH == jobInfo.action) {
            return runBlocking {
                refreshManager.performRefresh(
                    changeToken,
                    localeManager.locale,
                    randomValue,
                    providers
                )
            }
        }
        return JobResult.SUCCESS
    }

    public var foregroundRefreshInterval: Long
        get() = preferenceDataStore.getLong(
            FOREGROUND_REFRESH_INTERVAL_KEY,
            DEFAULT_FOREGROUND_REFRESH_INTERVAL_MS
        )
        set(milliseconds) {
            preferenceDataStore.put(FOREGROUND_REFRESH_INTERVAL_KEY, milliseconds)
        }

    public val randomValue: Int
        get() {
            var randomValue = preferenceDataStore.getInt(RANDOM_VALUE_KEY, -1)
            if (randomValue == -1) {
                val random = Random()
                randomValue = random.nextInt(MAX_RANDOM_VALUE + 1)
                preferenceDataStore.put(RANDOM_VALUE_KEY, randomValue)
            }
            return randomValue
        }

    private fun dispatchRefreshJob() {
        refreshManager.dispatchRefreshJob()
    }

    public fun notifyOutdated(remoteDataInfo: RemoteDataInfo) {
        providers.firstOrNull { it.source == remoteDataInfo.source }
            ?.notifyOutdated(remoteDataInfo)
    }

    public suspend fun payloads(types: List<String>): List<RemoteDataPayload> {
        if (types.isEmpty()) {
            return emptyList()
        }

        return providers.flatMap { it.payloads(types) }.sortedBy {
            types.indexOf(it.type)
        }
    }

    public suspend fun payloads(type: String): List<RemoteDataPayload> {
        return payloads(listOf(type))
    }

    public suspend fun refresh(): Boolean {
        return refresh(providers.map { it.source })
    }

    public suspend fun refresh(source: RemoteDataSource): Boolean {
        return refresh(listOf(source))
    }

    public suspend fun refresh(sources: List<RemoteDataSource>): Boolean {
        val providerSources = sources.intersect(providers.map { it.source }.toSet())
        if (providerSources.isEmpty()) return false

        // Filter the refreshes based on source, then combine them into an array. Then wait until
        // the array contains the # of sources to refresh before mapping the result.
        return refreshManager.refreshFlow
            .filter { providerSources.contains(it.first) }
            .runningFold(mutableListOf<RemoteDataProvider.RefreshResult>()) { acc, value ->
                acc.add(value.second)
                Logger.e { "$acc" }
                acc
            }
            .filter { it.size == providerSources.count() }
            .map { !it.contains(RemoteDataProvider.RefreshResult.FAILED) }
            .onStart {
                dispatchRefreshJob()
            }
            .first()
    }

    public fun payloadFlow(type: String): Flow<List<RemoteDataPayload>> {
        return payloadFlow(listOf(type))
    }

    public fun payloadFlow(types: List<String>): Flow<List<RemoteDataPayload>> {
        return refreshManager.refreshFlow
            .filter { it.second == RemoteDataProvider.RefreshResult.NEW_DATA }
            .map {
                payloads(types)
            }.onStart {
                emit(payloads(types))
            }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onUrlConfigUpdated() {
        updateChangeToken()
        dispatchRefreshJob()
    }

    private fun updateChangeToken() {
        changeTokenLock.withLock {
            this.dataStore.put(UUID.randomUUID().toString(), CHANGE_TOKEN_KEY)
        }
    }

    private val changeToken: String
        get() {
            return changeTokenLock.withLock {
                val token = this.dataStore.getString(CHANGE_TOKEN_KEY, "").ifEmpty {
                    val token = UUID.randomUUID().toString()
                    this.dataStore.put(CHANGE_TOKEN_KEY, token)
                    token
                }

                token + "$appVersion"
            }
        }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo): Boolean {
        return providers.firstOrNull { it.source == remoteDataInfo.source }
            ?.isCurrent(localeManager.locale, randomValue) ?: false
    }

    public companion object {

        // Datastore keys
        private const val FOREGROUND_REFRESH_INTERVAL_KEY = "com.urbanairship.remotedata.FOREGROUND_REFRESH_INTERVAL"
        private const val RANDOM_VALUE_KEY = "com.urbanairship.remotedata.RANDOM_VALUE"
        private const val CHANGE_TOKEN_KEY = "com.urbanairship.remotedata.CHANGE_TOKEN"

        /**
         * Default foreground refresh interval in milliseconds.
         */
        public const val DEFAULT_FOREGROUND_REFRESH_INTERVAL_MS: Long = 10000 // 10 seconds

        /**
         * Maximum random value.
         */
        private const val MAX_RANDOM_VALUE = 9999

        /**
         * Action to refresh remote data.
         *
         * @hide
         */
        @VisibleForTesting
        internal const val ACTION_REFRESH = "ACTION_REFRESH"

        private fun createProviders(
            context: Context,
            preferenceDataStore: PreferenceDataStore,
            config: AirshipRuntimeConfig,
            pushProviders: Supplier<PushProviders>,
            contact: Contact
        ): List<RemoteDataProvider> {
            val apiClient = RemoteDataApiClient(config)
            val urlFactory = RemoteDataUrlFactory(config, pushProvidersSupplier = pushProviders)
            return listOf(
                AppRemoteDataProvider(
                    context = context,
                    preferenceDataStore = preferenceDataStore,
                    config = config,
                    apiClient = apiClient,
                    urlFactory = urlFactory
                ),
                ContactRemoteDataProvider(
                    context = context,
                    preferenceDataStore = preferenceDataStore,
                    config = config,
                    contact = contact,
                    apiClient = apiClient,
                    urlFactory = urlFactory
                )
            )
        }
    }
}
