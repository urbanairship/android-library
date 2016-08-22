/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.sample;

import android.util.Log;

import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helper class to send push notifications.
 */
public class PushSender {
    private final String masterSecret;
    private final String appKey;
    private final String pushUrl;
    private final RequestFactory requestFactory;

    protected static String TAG = "PushSender";
    protected static final String PUSH_URL = "https://go.urbanairship.com/api/push/";

    /**
     * Constructor for PushSender
     *
     * @param masterSecret The specified master secret for the app
     * @param appKey The specified app key for the app
     */
    public PushSender(String masterSecret, String appKey) {
        this.masterSecret = masterSecret;
        this.appKey = appKey;
        this.pushUrl = PUSH_URL;
        this.requestFactory = new RequestFactory();
    }

    /**
     * Sends the push message.
     *
     * @param payload The PushPayload
     * @throws InterruptedException
     */
    public void send(PushPayload payload) throws InterruptedException {
        int sendMesgRetryCount = 0;
        int MAX_SEND_MESG_RETRIES = 3;

        while (sendMesgRetryCount < MAX_SEND_MESG_RETRIES) {
            Log.i(TAG, "Created message to send: " + payload.toJsonValue().toString());
            try {
                request(payload.toString());
                return;
            } catch (MalformedURLException e) {
                Log.e(TAG, "Failed to send message: " + payload.toJsonValue().toString(), e);
                int SEND_MESG_RETRY_DELAY = 3000;
                Thread.sleep(SEND_MESG_RETRY_DELAY);
                sendMesgRetryCount++;
            }
        }
    }

    /**
     * Actually sends the push message
     *
     * @param message The json formatted message to be sent
     * @throws MalformedURLException
     */
    private void request(String message) throws MalformedURLException {
        URL url = new URL(pushUrl);

        Response response = requestFactory.createRequest("POST", url)
                                          .setCredentials(appKey, masterSecret)
                                          .setRequestBody(message, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();
        if (response == null) {
            Log.e(TAG, "Failed to receive a response.");
        } else {
            Log.d(TAG, "Received a response: " + response);
        }
    }
}
