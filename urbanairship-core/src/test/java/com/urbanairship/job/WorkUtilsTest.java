package com.urbanairship.job;

import com.urbanairship.BaseTestCase;
import com.urbanairship.base.Extender;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Data;

import static org.junit.Assert.assertEquals;

public class WorkUtilsTest extends BaseTestCase {

    @Test
    public void testConvert() throws JsonException {
        JobInfo original = JobInfo.newBuilder()
                                  .setAction("some action")
                                  .setAirshipComponent(PushManager.class)
                                  .setConflictStrategy(JobInfo.APPEND)
                                  .setExtras(JsonMap.newBuilder()
                                                    .put("key", "value")
                                                    .build())
                                  .setMinDelay(10, TimeUnit.MILLISECONDS)
                                  .setNetworkAccessRequired(true)
                                  .setInitialBackOff(10, TimeUnit.SECONDS)
                                  .addRateLimit("foo")
                                  .addRateLimit("bar")
                                  .build();

        Data data = WorkUtils.convertToData(original);
        JobInfo converted = WorkUtils.convertToJobInfo(data);
        assertEquals(original, converted);
    }
}
