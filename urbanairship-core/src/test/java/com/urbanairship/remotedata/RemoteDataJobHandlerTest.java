/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.DateUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RemoteDataJobHandlerTest extends BaseTestCase {

    private RemoteDataApiClient client;
    private RemoteData remoteData;
    private RemoteDataJobHandler jobHandler;
    private JsonMap responsePayload;
    private LocaleManager localeManager;

    @Before
    public void setup() {
        client = Mockito.mock(RemoteDataApiClient.class);
        remoteData = Mockito.mock(RemoteData.class);
        localeManager = new LocaleManager(getApplication(), new PreferenceDataStore(getApplication()));

        jobHandler = new RemoteDataJobHandler(remoteData, client, localeManager);

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
    public void testRefreshRemoteDataSuccess() throws RequestException, JsonException {
        validateRemoteDataSuccess(200);
        validateRemoteDataSuccess(304);
    }

    /**
     * Test that fetching remote data retries on error
     */
    @Test
    public void testRefreshRemoteDataFailure() throws RequestException {
        validateRemoteDataFailure(501);
    }

    private Response<Set<RemoteDataPayload>> responseWithStatus(int status) {
        Response<Set<RemoteDataPayload>> response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(status);

        return response;
    }

    private Response<Set<RemoteDataPayload>> refreshSuccessResponse(int status) {
        Response<Set<RemoteDataPayload>> response = responseWithStatus(status);

        //JsonValue json = JsonValue.parseString("{\"payloads\":[{\"data\":{\"foo\":\"bar\"},\"type\":\"test\",\"timestamp\":\"2020-12-28T15:09:36\"}]}");
        Set<RemoteDataPayload> remoteDataPayloadSet = RemoteDataPayload.parsePayloads(JsonValue.wrapOpt(responsePayload), RemoteData.createMetadata(new Locale("de")));
        if (status == 200) {
            when(response.getResponseHeader("Last-Modified")).thenReturn("lastModifiedResponse");
            when(response.getResponseBody()).thenReturn(responsePayload.toString());
            when(response.getResult()).thenReturn(remoteDataPayloadSet);
        }

        return response;
    }

    private void validateRemoteDataSuccess(int status) throws RequestException, JsonException {
        Locale locale = new Locale("de");
        localeManager.setLocaleOverride(locale);
        JsonMap metadata = RemoteData.createMetadata(locale);

        clearInvocations(remoteData);

        Response<Set<RemoteDataPayload>> response = refreshSuccessResponse(status);

        when(client.fetchRemoteData("lastModifiedRequest", locale)).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteDataJobHandler.ACTION_REFRESH).build();
        Assert.assertEquals("Job should finish", JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify the update was performed
        verify(client).fetchRemoteData("lastModifiedRequest", locale);

        if (status == 200) {
            verify(remoteData).onNewData(response.getResult(), "lastModifiedResponse", metadata);
        }

        verify(remoteData).onRefreshFinished();
        reset(client);
    }

    private void validateRemoteDataFailure(int status) throws RequestException {
        Locale locale = new Locale("de");
        localeManager.setLocaleOverride(locale);
        Response<Set<RemoteDataPayload>> response = responseWithStatus(501);
        when(client.fetchRemoteData("lastModifiedRequest", locale)).thenReturn(response);

        // Perform the update
        JobInfo jobInfo = JobInfo.newBuilder().setAction(RemoteDataJobHandler.ACTION_REFRESH).build();
        Assert.assertEquals("Job should retry", JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));

        reset(client);
    }

}
