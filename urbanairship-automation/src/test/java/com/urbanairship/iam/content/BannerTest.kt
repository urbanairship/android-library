/* Copyright Airship and Contributors */
package com.urbanairship.iam.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.json.JsonValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerTest {

    @Test
    public fun durationIsParsedAsSeconds() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top", "duration": 15}"""))

        assertThat(banner.duration).isEqualTo(15.seconds)
    }

    @Test
    public fun fractionalDurationIsParsedAsSeconds() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top", "duration": 2.5}"""))

        assertThat(banner.duration).isEqualTo(2500.milliseconds)
    }

    @Test
    public fun missingDurationUsesTheDefault() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top"}"""))

        assertThat(banner.duration).isEqualTo(Banner.DEFAULT_DURATION)
    }

    @Test
    public fun durationIsWrittenAsSeconds() {
        val banner = banner(duration = 15.seconds)

        val json = banner.toJsonValue().optMap().opt("duration")

        assertThat(json.getDouble(0.0)).isEqualTo(15.0)
    }

    @Test
    public fun subSecondDurationRoundTrips() {
        val banner = banner(duration = 1500.milliseconds)

        assertThat(Banner.fromJson(banner.toJsonValue()).duration).isEqualTo(1500.milliseconds)
    }

    private fun banner(duration: Duration) = Banner(
        template = Banner.Template.MEDIA_LEFT,
        duration = duration,
        placement = Banner.Placement.TOP
    )
}
