package com.urbanairship.audience

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ShadowAirshipExecutorsLegacy
import com.urbanairship.TestApplication
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Config(
    sdk = [28],
    shadows = [ShadowAirshipExecutorsLegacy::class],
    application = TestApplication::class
)
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.LEGACY)
public class DeviceInfoProviderTest {

    private lateinit var infoProvider: DeviceInfoProvider
    private var context: Context = ApplicationProvider.getApplicationContext()

    @Before
    public fun setUp() {
        infoProvider = DeviceInfoProviderImpl(
            { true }, { true }, { emptySet() },
            { "channel-id" }, { 1 }, mockk(), { "contact-id" }
        )
    }

    @Test
    public fun testCutOffVersionIsRetreivedAndStoredCorrectly() {
        val packageManager = Shadows.shadowOf(TestApplication.getApplication().packageManager)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.firstInstallTime = 9191

        assertEquals(info.firstInstallTime, infoProvider.userCutOffDate(context))
    }
}
