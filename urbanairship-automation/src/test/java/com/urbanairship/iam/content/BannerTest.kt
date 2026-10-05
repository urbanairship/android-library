/* Copyright Airship and Contributors */
package com.urbanairship.iam.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.json.JsonValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerTest {

    @Test
    public fun durationIsParsedAsSeconds() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top", "duration": 15}"""))

        assertThat(banner.durationMs).isEqualTo(15_000L)
    }

    @Test
    public fun fractionalDurationIsParsedAsSeconds() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top", "duration": 2.5}"""))

        assertThat(banner.durationMs).isEqualTo(2_500L)
    }

    @Test
    public fun missingDurationUsesTheDefault() {
        val banner = Banner.fromJson(JsonValue.parseString("""{"placement": "top"}"""))

        assertThat(banner.durationMs).isEqualTo(Banner.DEFAULT_DURATION_MS)
    }

    @Test
    public fun durationIsWrittenAsSeconds() {
        val banner = banner(durationMs = 15_000L)

        val json = banner.toJsonValue().optMap().opt("duration")

        assertThat(json.getDouble(0.0)).isEqualTo(15.0)
    }

    @Test
    public fun subSecondDurationRoundTrips() {
        val banner = banner(durationMs = 1_500L)

        assertThat(Banner.fromJson(banner.toJsonValue()).durationMs).isEqualTo(1_500L)
    }

    private fun banner(durationMs: Long) = Banner(
        template = Banner.Template.MEDIA_LEFT,
        durationMs = durationMs,
        placement = Banner.Placement.TOP
    )
}
