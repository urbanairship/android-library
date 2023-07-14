package com.urbanairship.automation

import androidx.core.util.ObjectsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ApplicationMetrics
import com.urbanairship.ShadowAirshipExecutorsLegacy
import com.urbanairship.TestApplication
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceTagSelector
import com.urbanairship.automation.tags.TagSelector
import com.urbanairship.json.ValueMatcher
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Config(
    sdk = [28],
    shadows = [ShadowAirshipExecutorsLegacy::class],
    application = TestApplication::class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4::class)
public class AudienceMigrationTets {

    @Before
    public fun setup() {
        val mockMetrics: ApplicationMetrics = mockk()
        TestApplication.getApplication().setApplicationMetrics(mockMetrics)
    }

    @Test
    public fun testAudienceMigration() {
        val old = Audience.newBuilder()
            .addLanguageTag("en-US")
            .setNewUser(true)
            .setLocationOptIn(false)
            .setNotificationsOptIn(true)
            .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
            .setTagSelector(TagSelector.tag("some tag"))
            .addTestDevice("cool story")
            .setMissBehavior("cancel")
            .build()

        val new = AudienceSelector.fromJson(old.toJsonValue())

        assertTrue(ObjectsCompat.equals(new.languageTags, old.languageTags))
        assertTrue(ObjectsCompat.equals(new.newUser, old.newUser))
        assertTrue(ObjectsCompat.equals(new.locationOptIn, old.locationOptIn))
        assertTrue(ObjectsCompat.equals(new.notificationsOptIn, old.notificationsOptIn))
        assertTrue(ObjectsCompat.equals(new.versionPredicate, old.versionPredicate))
        assertTrue(ObjectsCompat.equals(new.tagSelector?.toJsonValue(), old.tagSelector?.toJsonValue()))
        assertTrue(ObjectsCompat.equals(new.testDevices, old.testDevices))
        assertTrue(ObjectsCompat.equals(new.missBehavior.value, old.missBehavior))
    }

    @Test
    public fun testTagSelectorMigration() {
        val old = TagSelector.or(
            TagSelector.and(
                TagSelector.tag("some-tag"), TagSelector.not(TagSelector.tag("not-tag"))
            ), TagSelector.tag("some-other-tag")
        )

        val new = DeviceTagSelector.fromJson(old.toJsonValue())
        assertTrue(ObjectsCompat.equals(new.toJsonValue(), old.toJsonValue()))
    }
}
