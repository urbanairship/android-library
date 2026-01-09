package com.urbanairship.job

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.job.JobInfo.Companion.newBuilder
import com.urbanairship.job.WorkUtils.convertToData
import com.urbanairship.job.WorkUtils.convertToJobInfo
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class WorkUtilsTest {

    @Test
    public fun testConvert() {
        val original = newBuilder()
            .setAction("some action")
            .setScope(PushManager::class.java.name)
            .setConflictStrategy(JobInfo.ConflictStrategy.APPEND)
            .setExtras(jsonMapOf("key" to "value"))
            .setMinDelay(10.milliseconds)
            .setNetworkAccessRequired(true)
            .setInitialBackOff(10.seconds)
            .addRateLimit("foo")
            .addRateLimit("bar")
            .build()

        val data = convertToData(original)
        val converted = convertToJobInfo(data)
        Assert.assertEquals(original, converted)
    }
}
