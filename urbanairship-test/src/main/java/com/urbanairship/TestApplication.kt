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
import com.urbanairship.job.JobInfo
import com.urbanairship.locale.LocaleManager
import com.urbanairship.modules.location.AirshipLocationClient
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager
import com.urbanairship.util.PlatformUtils
import java.lang.reflect.Method
import kotlin.time.Duration
import org.robolectric.TestLifecycleApplication

@SuppressLint("VisibleForTests")
public class TestApplication public constructor() : Application(), TestLifecycleApplication {

    public var callback: ActivityLifecycleCallbacks? = null

    internal lateinit var preferenceDataStore: PreferenceDataStore

    public fun getPreferenceDataStore(): PreferenceDataStore {
        return this.preferenceDataStore
    }

    @JvmField
    public var testRuntimeConfig: TestAirshipRuntimeConfig? = null

    override fun onCreate() {
        super.onCreate()

        testRuntimeConfig = TestAirshipRuntimeConfig()
        val airshipConfigOptions = testRuntimeConfig!!.configOptions

        this.preferenceDataStore = PreferenceDataStore.inMemoryStore(
            applicationContext
        )

        val dispatcher = JobDispatcher(this, { _, _, _ -> })
        setInstance(dispatcher)

        val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL)
        val pushProviders =
            Supplier<PushProviders?> { TestPushProviders(testRuntimeConfig!!.configOptions) }

        UAirship.application = this
        UAirship.isFlying = true
        UAirship.isTakingOff = true

        val audienceOverridesProvider = AudienceOverridesProvider()
        UAirship.sharedAirship = UAirship(airshipConfigOptions)
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore
        UAirship.sharedAirship.localeManager = LocaleManager(this, preferenceDataStore)
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig
        UAirship.sharedAirship.permissionsManager = PermissionsManager(this)
        val channelRegistrar = ChannelRegistrar(
            applicationContext, preferenceDataStore, testRuntimeConfig!!, privacyManager
        )
        UAirship.sharedAirship.channel = AirshipChannel(
            this,
            preferenceDataStore,
            UAirship.sharedAirship.runtimeConfig,
            privacyManager,
            UAirship.sharedAirship.permissionsManager,
            UAirship.sharedAirship.localeManager,
            audienceOverridesProvider,
            channelRegistrar
        )

        UAirship.sharedAirship.analytics = Analytics(
            this,
            preferenceDataStore,
            testRuntimeConfig!!,
            privacyManager,
            UAirship.sharedAirship.channel,
            UAirship.sharedAirship.localeManager,
            UAirship.sharedAirship.permissionsManager,
            AirshipEventFeed(privacyManager, true)
        )
        UAirship.sharedAirship.applicationMetrics =
            ApplicationMetrics(this, preferenceDataStore, privacyManager, TestActivityMonitor())
        UAirship.sharedAirship.pushManager = PushManager(
            this,
            preferenceDataStore,
            testRuntimeConfig!!,
            privacyManager,
            pushProviders,
            UAirship.sharedAirship.channel,
            UAirship.sharedAirship.analytics,
            UAirship.sharedAirship.permissionsManager
        )
        UAirship.sharedAirship.channelCapture = ChannelCapture(
            this,
            airshipConfigOptions,
            UAirship.sharedAirship.channel,
            preferenceDataStore,
            TestActivityMonitor()
        )
        UAirship.sharedAirship.urlAllowList =
            UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions)
        UAirship.sharedAirship.actionRegistry = ActionRegistry()
        UAirship.sharedAirship.actionRegistry.registerDefaultActions(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        preferenceDataStore.tearDown()
    }

    public fun setPlatform(platform: Int) {
        testRuntimeConfig!!.setPlatform(PlatformUtils.parsePlatform(platform))
    }

    public fun setPrivacyManager(privacyManager: PrivacyManager?) {
        UAirship.shared().privacyManager = privacyManager
    }

    public fun setApplicationMetrics(metrics: ApplicationMetrics?) {
        UAirship.shared().applicationMetrics = metrics
    }

    public fun setContact(contact: Contact?) {
        UAirship.shared().contact = contact
    }

    public fun setAnalytics(analytics: Analytics?) {
        UAirship.shared().analytics = analytics
    }

    public fun setOptions(options: AirshipConfigOptions?) {
        UAirship.shared().airshipConfigOptions = options
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

    public fun setChannel(channel: AirshipChannel?) {
        UAirship.shared().channel = channel
    }

    public fun setPushManager(pushManager: PushManager?) {
        UAirship.shared().pushManager = pushManager
    }

    public fun setLocationClient(locationClient: AirshipLocationClient?) {
        UAirship.shared().locationClient = locationClient
    }

    public fun setChannelCapture(channelCapture: ChannelCapture?) {
        UAirship.shared().channelCapture = channelCapture
    }

    public companion object {

        @JvmStatic
        public fun getApplication(): TestApplication {
            return ApplicationProvider.getApplicationContext<Context>() as TestApplication
        }
    }
}
