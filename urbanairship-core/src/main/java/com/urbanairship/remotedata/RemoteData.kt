/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.JobAwareAirshipComponent
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.PrivacyManager
import com.urbanairship.PushProviders
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val preferenceStore: PreferenceStore,
    private val privacyManager: PrivacyManager,
    private val localeManager: LocaleManager,
    private val pushManager: PushManager,
    private val contact: Contact,
    private val providers: List<RemoteDataProvider>,
    private val appVersion: Long,
    private val refreshManager: RemoteDataRefreshManager,
    private val activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    coroutineDispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : JobAwareAirshipComponent(context, preferenceStore) {

    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    private var lastForegroundDispatchTime: Long = 0
    private val changeTokenLock: Lock = ReentrantLock()

    private var startUpRefreshJob: Job? = null
    private var foregroundPollingJob: Job? = null

    private var airshipReady: MutableStateFlow<Boolean> = MutableStateFlow(false)

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

    internal fun getRefreshInterval(): Duration {
        return config.remoteConfig.remoteDataRefreshInterval ?: DEFAULT_FOREGROUND_REFRESH_INTERVAL
    }

    internal fun getForegroundPollingInterval(): Duration {
        return config.remoteConfig.remoteDataForegroundPollingInterval ?: DEFAULT_FOREGROUND_POLLING_INTERVAL
    }

    internal constructor(
        context: Context,
        config: AirshipRuntimeConfig,
        preferenceStore: PreferenceStore,
        privacyManager: PrivacyManager,
        localeManager: LocaleManager,
        pushManager: PushManager,
        pushProviders: () -> PushProviders,
        contact: Contact,
        providers: List<RemoteDataProvider> = createProviders(
            context = context,
            preferenceStore = preferenceStore,
            config = config,
            pushProvidersProvider = pushProviders,
            contact = contact
        )
    ) : this(
        context = context,
        config = config,
        preferenceStore = preferenceStore,
        privacyManager = privacyManager,
        localeManager = localeManager,
        pushManager = pushManager,
        contact = contact,
        providers = providers,
        appVersion = context
            .packageManager
            .getPackageInfo(context.packageName, 0)
            ?.let { PackageInfoCompat.getLongVersionCode(it) }
            ?: -1,
        refreshManager = RemoteDataRefreshManager(
            JobDispatcher.shared(context),
            privacyManager,
            providers
        )
    )

    private val applicationListener: ApplicationListener = object : SimpleApplicationListener() {
        override fun onForeground(milliseconds: Long) {
            val now = clock.currentTimeMillis()
            if (now >= lastForegroundDispatchTime + getRefreshInterval().inWholeMilliseconds) {
                updateChangeToken()
                dispatchRefreshJobAsync()
                lastForegroundDispatchTime = now
            }
            startForegroundPollingIfNeeded()
        }

        override fun onBackground(milliseconds: Long) {
            stopForegroundPolling()
        }
    }

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
        updateChangeToken()
        pushManager.addInternalPushListener(pushListener)
        privacyManager.addListener(privacyListener)
        config.addConfigListener(configListener)

        scope.launch {
            localeManager.localeUpdates.collect { dispatchRefreshJob() }
        }

        scope.launch {
            contact.contactIdUpdateFlow.mapNotNull { it?.contactId }.distinctUntilChanged()
                .collect {
                    dispatchRefreshJob()
                }
        }

        scope.launch {
            refreshManager.refreshFlow.collect {
                val refreshStatus = when (it.second) {
                    is RemoteDataProvider.RefreshResult.Skipped -> RefreshStatus.SUCCESS
                    is RemoteDataProvider.RefreshResult.NewData -> RefreshStatus.SUCCESS
                    is RemoteDataProvider.RefreshResult.Failed -> RefreshStatus.FAILED
                }
                refreshStatusFlowMap[it.first]?.emit(refreshStatus)
            }
        }

        // For the first refresh bypass work manager since it can delay the job in
        // attempt to speed up initial refresh
        val changeToken = changeToken
        val randomValue = randomValue
        this.startUpRefreshJob = scope.launch {
            airshipReady.first { it }
            refreshManager.performRefresh(
                changeToken = changeToken,
                locale = localeManager.locale,
                randomValue = randomValue
            )
        }

        activityMonitor.addApplicationListener(applicationListener)

        if (activityMonitor.isAppForegrounded) {
            applicationListener.onForeground(clock.currentTimeMillis())
        }
    }

    public override fun onAirshipReady() {
        super.onAirshipReady()
        airshipReady.update { true }
        startForegroundPollingIfNeeded()
    }

    public override fun tearDown() {
        pushManager.removePushListener(pushListener)
        activityMonitor.removeApplicationListener(applicationListener)
        privacyManager.removeListener(privacyListener)
        config.removeRemoteConfigListener(configListener)
        stopForegroundPolling()
    }

    private fun startForegroundPollingIfNeeded() {
        if (foregroundPollingJob?.isActive == true) return
        if (!activityMonitor.isAppForegrounded) return
        foregroundPollingJob = scope.launch {
            while (isActive) {
                taskSleeper.sleep(getForegroundPollingInterval())
                if (!isActive) return@launch
                dispatchRefreshJob()
            }
        }
    }

    private fun stopForegroundPolling() {
        foregroundPollingJob?.cancel()
        foregroundPollingJob = null
    }

    override val jobActions: List<String>
        get() = listOf(ACTION_REFRESH)

    override suspend fun onPerformJob(jobInfo: JobInfo): JobResult {
        return if (ACTION_REFRESH == jobInfo.action) {
            refreshManager.performRefresh(
                changeToken,
                localeManager.locale,
                randomValue
            )
        } else {
            JobResult.SUCCESS
        }
    }

    public val randomValue: Int
        get() {
            var randomValue = preferenceStore.get(RANDOM_VALUE_KEY) ?: -1
            if (randomValue == -1) {
                val random = SecureRandom()
                randomValue = random.nextInt(MAX_RANDOM_VALUE + 1)
                preferenceStore.put(RANDOM_VALUE_KEY, randomValue)
            }
            return randomValue
        }

    private fun dispatchRefreshJobAsync() {
        scope.launch {
            dispatchRefreshJob()
        }
    }

    private suspend fun dispatchRefreshJob() {
        // Wait for startup job to finish
        startUpRefreshJob?.join()
        startUpRefreshJob = null
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
            }.filter {
                it.size == providerSources.count()
            }.map { collection ->
                !collection.any { item ->
                    item is RemoteDataProvider.RefreshResult.Failed
                }
            }.onStart {
                dispatchRefreshJob()
            }.first()
    }

    public fun refreshStatusFlow(source: RemoteDataSource): StateFlow<RefreshStatus> {
        return refreshStatusFlowMap[source]?.asStateFlow() ?: MutableStateFlow(RefreshStatus.NONE).asStateFlow()
    }

    public fun payloadFlow(type: String): Flow<List<RemoteDataPayload>> {
        return payloadFlow(listOf(type))
    }

    public fun payloadFlow(types: List<String>): Flow<List<RemoteDataPayload>> {
        return refreshManager.refreshFlow
            .filter { it.second is RemoteDataProvider.RefreshResult.NewData }
            .map {
                payloads(types)
            }.onStart {
                emit(payloads(types))
            }
            .conflate()
    }

    private fun updateChangeToken() {
        changeTokenLock.withLock {
            this.dataStore.put(CHANGE_TOKEN_KEY, UUID.randomUUID().toString())
        }
    }

    private val changeToken: String
        get() {
            return changeTokenLock.withLock {
                val token = (this.dataStore.get(CHANGE_TOKEN_KEY) ?: "").ifEmpty {
                    val token = UUID.randomUUID().toString()
                    this.dataStore.put(CHANGE_TOKEN_KEY, token)
                    token
                }

                "$token:$appVersion"
            }
        }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo): Boolean {
        return providers.firstOrNull { it.source == remoteDataInfo.source }
            ?.isCurrent(localeManager.locale, randomValue, remoteDataInfo) ?: false
    }

    public fun status(source: RemoteDataSource): Status {
        return statusFlow(source)?.value ?: Status.OUT_OF_DATE
    }

    public fun statusFlow(source: RemoteDataSource): StateFlow<Status>? {
        val provider = providers
            .firstOrNull { it.source == source }
            ?: return null

        provider.status(changeToken, localeManager.locale, randomValue)
        return provider.statusUpdates
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
        private val RANDOM_VALUE_KEY = SyncPrefKey.int("com.urbanairship.remotedata.RANDOM_VALUE")
        private val CHANGE_TOKEN_KEY = SyncPrefKey.string("com.urbanairship.remotedata.CHANGE_TOKEN")

        /**
         * Default foreground refresh interval.
         */
        public val DEFAULT_FOREGROUND_REFRESH_INTERVAL: Duration = 10.seconds

        /**
         * Default foreground polling interval.
         */
        public val DEFAULT_FOREGROUND_POLLING_INTERVAL: Duration = 10.minutes

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
            preferenceStore: PreferenceStore,
            config: AirshipRuntimeConfig,
            pushProvidersProvider: () -> PushProviders,
            contact: Contact
        ): List<RemoteDataProvider> {
            val apiClient = RemoteDataApiClient(config)
            val urlFactory = RemoteDataUrlFactory(config, pushProvidersProvider = pushProvidersProvider)
            return listOf(
                AppRemoteDataProvider(
                    context = context,
                    preferenceStore = preferenceStore,
                    config = config,
                    apiClient = apiClient,
                    urlFactory = urlFactory
                ),
                ContactRemoteDataProvider(
                    context = context,
                    preferenceStore = preferenceStore,
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
