/* Copyright Airship and Contributors */
package com.urbanairship

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.base.Supplier
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.ChannelRegistrar
import com.urbanairship.contacts.Contact
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobDispatcher.Companion.setInstance
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager
import java.lang.reflect.Method
import org.robolectric.TestLifecycleApplication

@SuppressLint("VisibleForTests")
public class TestApplication public constructor() : Application(), TestLifecycleApplication {

    public var callback: ActivityLifecycleCallbacks? = null

    private lateinit var preferenceDataStore: PreferenceDataStore

    public fun getPreferenceDataStore(): PreferenceDataStore {
        return this.preferenceDataStore
    }

    public lateinit var testRuntimeConfig: TestAirshipRuntimeConfig

    override fun onCreate() {
        super.onCreate()

        testRuntimeConfig = TestAirshipRuntimeConfig()
        val airshipConfigOptions = testRuntimeConfig.configOptions

        this.preferenceDataStore = PreferenceDataStore.inMemoryStore(
            applicationContext
        )

        val dispatcher = JobDispatcher(this, { _, _, _ -> })
        setInstance(dispatcher)

        val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL)
        val pushProviders =
            Supplier<PushProviders> { TestPushProviders(testRuntimeConfig.configOptions) }

        Airship.application = this
        Airship.isFlying = true
        Airship.isTakingOff = true

        val audienceOverridesProvider = AudienceOverridesProvider()
        val airship = Airship(airshipConfigOptions)
        airship.preferenceDataStore = preferenceDataStore
        airship.localeManager = LocaleManager(this, preferenceDataStore)
        airship.runtimeConfig = testRuntimeConfig
        airship.permissionsManager = PermissionsManager(this)

        val channelRegistrar = ChannelRegistrar(
            applicationContext, preferenceDataStore, testRuntimeConfig, privacyManager
        )
        airship.channel = AirshipChannel(
            this,
            preferenceDataStore,
            airship.runtimeConfig,
            privacyManager,
            airship.permissionsManager,
            airship.localeManager,
            audienceOverridesProvider,
            channelRegistrar
        )

        airship.analytics = Analytics(
            this,
            preferenceDataStore,
            testRuntimeConfig,
            privacyManager,
            airship.channel,
            airship.localeManager,
            airship.permissionsManager,
            AirshipEventFeed(privacyManager, true)
        )
        airship.applicationMetrics =
            ApplicationMetrics(this, preferenceDataStore, privacyManager, TestActivityMonitor())
        airship.pushManager = PushManager(
            this,
            preferenceDataStore,
            testRuntimeConfig,
            privacyManager,
            pushProviders,
            airship.channel,
            airship.analytics,
            airship.permissionsManager
        )
        airship.channelCapture = ChannelCapture(
            this,
            airshipConfigOptions,
            airship.channel,
            preferenceDataStore,
            TestActivityMonitor()
        )
        airship.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions)
        airship.actionRegistry = ActionRegistry()
        airship.actionRegistry.registerDefaultActions(this)

        Airship.sharedAirship = airship
    }

    override fun onTerminate() {
        super.onTerminate()
        preferenceDataStore.tearDown()
    }

    public fun setPlatform(platform: Airship.Platform) {
        testRuntimeConfig.setPlatform(platform)
    }

    public fun setPrivacyManager(privacyManager: PrivacyManager) {
        Airship.shared().privacyManager = privacyManager
    }

    public fun setApplicationMetrics(metrics: ApplicationMetrics) {
        Airship.shared().applicationMetrics = metrics
    }

    public fun setContact(contact: Contact) {
        Airship.shared().contact = contact
    }

    public fun setAnalytics(analytics: Analytics) {
        Airship.shared().analytics = analytics
    }

    public fun setOptions(options: AirshipConfigOptions) {
        Airship.shared().airshipConfigOptions = options
    }

    override fun afterTest(method: Method) {
        preferenceDataStore.tearDown()
    }

    override fun beforeTest(method: Method) {
    }

    override fun prepareTest(test: Any) {
    }

    @SuppressLint("NewApi")
    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        super.registerActivityLifecycleCallbacks(callback)
        this.callback = callback
    }

    public fun setChannel(channel: AirshipChannel) {
        Airship.shared().channel = channel
    }

    public fun setPushManager(pushManager: PushManager) {
        Airship.shared().pushManager = pushManager
    }

    public fun setChannelCapture(channelCapture: ChannelCapture) {
        Airship.shared().channelCapture = channelCapture
    }

    public companion object {
        public fun getApplication(): TestApplication {
            return ApplicationProvider.getApplicationContext<Context>() as TestApplication
        }
    }
}
