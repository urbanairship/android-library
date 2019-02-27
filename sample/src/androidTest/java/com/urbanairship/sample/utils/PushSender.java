/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.urbanairship.sample.data.model.PushPayload;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Helper class to send push notifications.
 */
public class PushSender {
    private final String masterSecret;
    private final String appKey;
    private final String pushUrl;
    private final OkHttpClient client;
    private final Retrofit retrofit;
    private final PushService service;
    HttpLoggingInterceptor interceptor;

    protected static String TAG = "PushSender";
    protected static final String PUSH_URL = "https://go.urbanairship.com/api/";

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

        this.interceptor = new HttpLoggingInterceptor();
        this.interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        this.client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        this.retrofit = new Retrofit.Builder()
                                    .baseUrl(this.pushUrl)
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .client(client)
                                    .build();
        this.service = retrofit.create(PushService.class);
    }

    /**
     * Sends the push message.
     *
     * @param payload The PushPayload
     */
    public void send(com.urbanairship.sample.utils.PushPayload payload) {

        Gson gson = new Gson();
        PushPayload gsonPayload = gson.fromJson(payload.toString(), PushPayload.class);
        Call<ResponseBody> call = service.send(Credentials.basic(appKey, masterSecret), gsonPayload);

        try {
            retrofit2.Response response = call.execute();
            // 5xx
            if (response == null || UAHttpStatusUtil.inServerErrorRange(response.code())) {
                Log.e(TAG, "Failed to receive a response. Will retry");
            }
            // 4xx
            if (UAHttpStatusUtil.inClientErrorRange(response.code())) {
                Log.e(TAG, "Failed to send message: " + payload.toString() + ". Received a response: " + response);
            }
            // 2xx
            if (UAHttpStatusUtil.inSuccessRange(response.code())) {
                Log.d(TAG, "Received a response: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
