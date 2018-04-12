/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.DateUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RemoteDataJobHandlerTest extends BaseTestCase {

    private RemoteDataApiClient client;
    private RemoteData remoteData;
    private RemoteDataJobHandler jobHandler;
    private JsonMap responsePayload;

    @Before
    public void setup() {
        client = Mockito.mock(RemoteDataApiClient.class);
        remoteData = Mockito.mock(RemoteData.class);

        TestApplication.getApplication().setRemoteData(remoteData);

        jobHandler = new RemoteDataJobHandler(TestApplication.getApplication(), UAirship.shared(), client);

        when(remoteData.getLastModified()).thenReturn("lastModifiedRequest");

        String responseTimestamp = DateUtils.createIso8601TimeStamp(System.currentTimeMillis());
        JsonMap data = JsonMap.newBuilder().put("foo", "bar").build();
        JsonMap payload = JsonMap.newBuilder().put("type", "test").put("timestamp", responseTimestamp).put("data", data).build();
        JsonList list = new JsonList(Arrays.asList(payload.toJsonValue()));
        responsePayload = JsonMap.newBuilder().put("payloads", list).build();
    }

    /**
     * Test that fetching remote data succeeds if the status is 200 or 304
     */
    @Test
    public void testRefreshRemoteDataSuccess() {
        validateRemoteDataSuccess(200);
        validateRemoteDataSuccess(304);
    }

    /**
     * Test that fetching remote data retries on error
     */
    @Test
    public void testRefreshRemoteDataFailure() {
        validateRemoteDataFailure(501);
    }

    private Response responseWithStatus(int status) {
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(status);

        return response;
    }

    private Response refreshSuccessResponse(int status) {
        Response response = responseWithStatus(status);

        if (status == 200) {
            when(response.getResponseHeader("Last-Modified")).thenReturn("lastModifiedResponse");
            when(response.getResponseBody()).thenReturn(responsePayload.toString());
        }

        return response;
    }

    private void validateRemoteDataSuccess(int status) {
        clearInvocations(remoteData);

        Response response = refreshSuccessResponse(status);

        when(client.fetchRemoteData("lastModifiedRequest")).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteDataJobHandler.ACTION_REFRESH).build();
        Assert.assertEquals("Job should finish", JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(client).fetchRemoteData("lastModifiedRequest");

        if (status == 200) {
            verify(remoteData).setLastModified("lastModifiedResponse");
            verify(remoteData).handleRefreshResponse(RemoteDataPayload.parsePayloads(responsePayload.get("payloads")));
        }

        verify(remoteData).onRefreshFinished();


        reset(client);
    }

    private void validateRemoteDataFailure(int status) {
        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteDataJobHandler.ACTION_REFRESH).build();
        Assert.assertEquals("Job should retry", JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        reset(client);
    }
}
