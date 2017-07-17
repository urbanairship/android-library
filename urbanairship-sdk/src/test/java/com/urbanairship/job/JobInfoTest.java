package com.urbanairship.job;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushManager;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;


public class JobInfoTest extends BaseTestCase {

    @Test
    public void testGenerateUniqueId() {
        // Verify the generated ID is between 49-99
        for (int i = 49; i < 99; i++) {
            JobInfo jobInfo = JobInfo.newBuilder()
                             .setAction("test_action")
                             .generateUniqueId(TestApplication.getApplication())
                             .setAirshipComponent(PushManager.class)
                             .build();

            assertEquals(jobInfo.getId(), i);
        }

        // Verify the generated ID wraps starts over after 99
        for (int i = 49; i < 99; i++) {
            JobInfo jobInfo = JobInfo.newBuilder()
                                     .setAction("test_action")
                                     .generateUniqueId(TestApplication.getApplication())
                                     .setAirshipComponent(PushManager.class)
                                     .build();

            assertEquals(jobInfo.getId(), i);
        }
    }
}