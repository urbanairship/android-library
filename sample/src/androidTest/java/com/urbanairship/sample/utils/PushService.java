/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.sample.utils;

import com.urbanairship.sample.data.model.PushPayload;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface PushService {

    @Headers({
            "Content-Type: application/json",
            "Accept: application/vnd.urbanairship+json; version=3"
    })
    @POST("push")
    Call<ResponseBody> send(@Header("Authorization") String authorization, @Body PushPayload payload);
}
