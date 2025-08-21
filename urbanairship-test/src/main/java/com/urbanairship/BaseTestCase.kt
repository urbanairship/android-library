/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.Bundle
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.shadow.ShadowNotificationManagerExtension
import java.util.concurrent.ExecutorService
import org.junit.Assert
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(
    sdk = [28],
    shadows = [ShadowNotificationManagerExtension::class, ShadowAirshipExecutorsPaused::class]
)
@RunWith(AndroidJUnit4::class)
public abstract class BaseTestCase public constructor() {

    public fun shadowMainLooper(): ShadowLooper {
        return Shadows.shadowOf(Looper.getMainLooper())
    }

    public fun shadowBackgroundLooper(): ShadowLooper {
        return Shadows.shadowOf(AirshipLoopers.backgroundLooper)
    }

    public fun shadowThreadPoolExecutor(): ExecutorService {
        return AirshipExecutors.threadPoolExecutor()
    }

    public companion object {

        public fun assertBundlesEquals(expected: Bundle, actual: Bundle) {
            assertBundlesEquals(null, expected, actual)
        }

        public fun assertBundlesEquals(message: String?, expected: Bundle, actual: Bundle) {
            if (!areEqual(expected, actual)) {
                Assert.fail("$message <$expected> is not equal to <$actual>")
            }
        }

        public fun areEqual(expected: Bundle?, actual: Bundle?): Boolean {
            if (expected == null) {
                return actual == null
            }

            if (expected.size() != actual!!.size()) {
                return false
            }

            for (key in expected.keySet()) {
                if (!actual.containsKey(key)) {
                    return false
                }

                val expectedValue = expected[key]
                val actualValue = actual[key]

                if (expectedValue == null) {
                    if (actualValue != null) {
                        return false
                    }

                    continue
                }

                if (expectedValue is Bundle && actualValue is Bundle) {
                    if (!areEqual(expectedValue, actualValue)) {
                        return false
                    }

                    continue
                }

                if (expectedValue != actualValue) {
                    return false
                }
            }

            return true
        }
    }
}
