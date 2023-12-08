/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PushProviders
import com.urbanairship.UALog
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * RemoteData top-level class.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteData @VisibleForTesting internal constructor(
    context: Context,
    private val config: AirshipRuntimeConfig,
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

    private val refreshStatusFlowMap = providers.associate {
        it.source to MutableStateFlow(RefreshStatus.NONE)
    }

    internal fun setContactSourceEnabled(enabled: Boolean) {
        val provider = this.providers.firstOrNull {
            it.source == RemoteDataSource.CONTACT
        } ?: return
        if (provider.isEnabled != enabled) {
            provider.isEnabled = enabled
        }
    }

    @JvmOverloads
    internal constructor(
        context: Context,
        config: AirshipRuntimeConfig,
        preferenceDataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        localeManager: LocaleManager,
        pushManager: PushManager,
        pushProviders: Supplier<PushProviders>,
        contact: Contact,
        providers: List<RemoteDataProvider> = createProviders(
            context = context,
            preferenceDataStore = preferenceDataStore,
            config = config,
            pushProviders = pushProviders,
            contact = contact
        )
    ) : this(
        context = context,
        config = config,
        preferenceDataStore = preferenceDataStore,
        privacyManager = privacyManager,
        localeManager = localeManager,
        pushManager = pushManager,
        contact = contact,
        providers = providers,
        appVersion = UAirship.getAppVersion(),
        refreshManager = RemoteDataRefreshManager(
            JobDispatcher.shared(context),
            privacyManager,
            providers
        )
    )

    private val applicationListener: ApplicationListener = object : SimpleApplicationListener() {
        override fun onForeground(time: Long) {
            val now = clock.currentTimeMillis()
            if (now >= lastForegroundDispatchTime + foregroundRefreshInterval) {
                updateChangeToken()
                dispatchRefreshJobAsync()
                lastForegroundDispatchTime = now
            }
        }
    }

    private val localeChangedListener = LocaleChangedListener { dispatchRefreshJobAsync() }
    private val pushListener = PushListener { message: PushMessage, _ ->
        if (message.isRemoteDataUpdate) {
            updateChangeToken()
            dispatchRefreshJobAsync()
        }
    }

    private val configListener = AirshipRuntimeConfig.ConfigChangeListener {
        setContactSourceEnabled(config.remoteConfig.fetchContactRemoteData ?: false)
        updateChangeToken()
        dispatchRefreshJobAsync()
    }

    private var isAnyFeatureEnabled = AtomicBoolean(privacyManager.isAnyFeatureEnabled)

    private val privacyListener = PrivacyManager.Listener {
        val newValue = privacyManager.isAnyFeatureEnabled

        if (!isAnyFeatureEnabled.getAndSet(newValue) && newValue) {
            dispatchRefreshJobAsync()
        }
    }

    init {
        activityMonitor.addApplicationListener(applicationListener)
        pushManager.addInternalPushListener(pushListener)
        localeManager.addListener(localeChangedListener)
        privacyManager.addListener(privacyListener)
        config.addConfigListener(configListener)

        scope.launch {
            contact.contactIdUpdateFlow.mapNotNull { it?.contactId }.distinctUntilChanged().collect {
                dispatchRefreshJob()
            }
        }

        scope.launch {
            refreshManager.refreshFlow.collect {
                val refreshStatus = when (it.second) {
                    RemoteDataProvider.RefreshResult.SKIPPED -> RefreshStatus.SUCCESS
                    RemoteDataProvider.RefreshResult.NEW_DATA -> RefreshStatus.SUCCESS
                    RemoteDataProvider.RefreshResult.FAILED -> RefreshStatus.FAILED
                }
                refreshStatusFlowMap[it.first]?.emit(refreshStatus)
            }
        }

        refreshManager.dispatchRefreshJob()

        if (activityMonitor.isAppForegrounded()) {
            applicationListener.onForeground(clock.currentTimeMillis())
        }
    }

    public override fun tearDown() {
        pushManager.removePushListener(pushListener)
        activityMonitor.removeApplicationListener(applicationListener)
        localeManager.removeListener(localeChangedListener)
        privacyManager.removeListener(privacyListener)
        config.removeRemoteConfigListener(configListener)
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
                    randomValue
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

    private fun dispatchRefreshJobAsync() {
        scope.launch {
            dispatchRefreshJob()
        }
    }

    private suspend fun dispatchRefreshJob() {
        refreshStatusFlowMap.values.forEach { it.emit(RefreshStatus.NONE) }
        refreshManager.dispatchRefreshJob()
    }

    public suspend fun notifyOutdated(remoteDataInfo: RemoteDataInfo) {
        val provider = providers.firstOrNull { it.source == remoteDataInfo.source }
        if (provider?.notifyOutdated(remoteDataInfo) == true) {
            dispatchRefreshJob()
        }
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
                acc
            }
            .filter { it.size == providerSources.count() }
            .map { !it.contains(RemoteDataProvider.RefreshResult.FAILED) }
            .onStart {
                dispatchRefreshJob()
            }
            .first()
    }

    public fun refreshStatusFlow(source: RemoteDataSource): StateFlow<RefreshStatus> {
        return refreshStatusFlowMap[source]?.asStateFlow() ?: MutableStateFlow(RefreshStatus.NONE).asStateFlow()
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

    private fun updateChangeToken() {
        changeTokenLock.withLock {
            this.dataStore.put(CHANGE_TOKEN_KEY, UUID.randomUUID().toString())
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

                "$token:$appVersion"
            }
        }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo): Boolean {
        return providers.firstOrNull { it.source == remoteDataInfo.source }
            ?.isCurrent(localeManager.locale, randomValue) ?: false
    }

    public fun status(source: RemoteDataSource): Status {
        return providers
            .firstOrNull { it.source == source }
            ?.status(changeToken, localeManager.locale, randomValue)
            ?: Status.OUT_OF_DATE
    }

    /**
     * Waits for remote-data `source` to successfully refresh up to the specified `maxTimeMillis`.
     */
    public suspend fun waitForRefresh(source: RemoteDataSource, maxTimeMillis: Long? = null) {
        UALog.v { "Waiting for remote data to refresh successfully $source" }
        waitForRefresh(source, maxTimeMillis) {
            it == RefreshStatus.SUCCESS
        }
    }

    /**
     * Waits for remote-data `source` to try to refresh up to the specified `maxTimeMillis`.
     */
    public suspend fun waitForRefreshAttempt(source: RemoteDataSource, maxTimeMillis: Long? = null) {
        UALog.v { "Waiting for remote data to refresh $source" }
        waitForRefresh(source, maxTimeMillis) {
            it != RefreshStatus.NONE
        }
    }

    private suspend fun waitForRefresh(source: RemoteDataSource, maxTimeMillis: Long?, predicate: suspend (RefreshStatus) -> Boolean) {
        val flow = refreshStatusFlow(source)
        val refreshStatus: RefreshStatus = if (maxTimeMillis != null) {
            withTimeoutOrNull(maxTimeMillis) {
                flow.firstOrNull(predicate)
            }
        } else {
            flow.firstOrNull(predicate)
        } ?: flow.value

        UALog.v { "Remote data refresh result: $source status: $refreshStatus" }
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

    public enum class Status {
        UP_TO_DATE, STALE, OUT_OF_DATE
    }

    public enum class RefreshStatus {
        NONE, FAILED, SUCCESS
    }
}
