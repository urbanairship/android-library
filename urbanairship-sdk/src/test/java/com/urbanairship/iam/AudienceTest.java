/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;


import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.ValueMatcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link Audience} tests.
 */
public class AudienceTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        Audience original = Audience.newBuilder()
                                    .addLanguageTag("en-US")
                                    .setNewUser(true)
                                    .setLocationOptIn(false)
                                    .setNotificationsOptIn(true)
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                    .setTagSelector(TagSelector.tag("some tag"))
                                    .build();

        Audience fromJson = Audience.parseJson(original.toJsonValue());
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
                                                                      .setKey("android")
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
                                                                      .setKey("amazon")
                                                                      .setValueMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                                                                      .build())
                                               .build();

        assertEquals(predicate, audience.getVersionPredicate());
    }
}