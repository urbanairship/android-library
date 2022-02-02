/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.ValueMatcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * {@link Audience} tests.
 */
@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class AudienceTest {

    private ApplicationMetrics mockMetrics;

    @Before
    public void setup() {
        mockMetrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(mockMetrics);
    }

    @Test
    public void testJson() throws JsonException {
        Audience original = Audience.newBuilder()
                                    .addLanguageTag("en-US")
                                    .setNewUser(true)
                                    .setLocationOptIn(false)
                                    .setNotificationsOptIn(true)
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                    .setTagSelector(TagSelector.tag("some tag"))
                                    .addTestDevice("cool story")
                                    .setMissBehavior("cancel")
                                    .build();

        Audience fromJson = Audience.fromJson(original.toJsonValue());
        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test
    public void testAndroidVersionMatcher() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
        Audience audience = Audience.newBuilder()
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                    .build();

        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setKey("version")
                                                                      .setScope("android")
                                                                      .setValueMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                                                      .build())
                                               .build();

        assertEquals(predicate, audience.getVersionPredicate());
    }

    @Test
    public void testAmazonVersionMatcher() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        Audience audience = Audience.newBuilder()
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                    .build();

        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setKey("version")
                                                                      .setScope("amazon")
                                                                      .setValueMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                                                      .build())
                                               .build();

        assertEquals(predicate, audience.getVersionPredicate());
    }

    @Test (expected = JsonException.class)
    public void testNotValidMissBehavior() throws JsonException {
        Audience original = Audience.newBuilder()
                                    .addLanguageTag("en-US")
                                    .setNewUser(true)
                                    .setLocationOptIn(false)
                                    .setNotificationsOptIn(true)
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                    .setTagSelector(TagSelector.tag("some tag"))
                                    .addTestDevice("cool story")
                                    .setMissBehavior("bad behavior")
                                    .build();

        Audience.fromJson(original.toJsonValue());
    }
}
