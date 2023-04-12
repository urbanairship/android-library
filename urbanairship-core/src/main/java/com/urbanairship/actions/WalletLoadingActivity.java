/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.activity.ThemedActivity;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

public class WalletLoadingActivity extends ThemedActivity {

    private final MutableLiveData<Result> liveData = new MutableLiveData<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ua_activity_wallet_loading);

        Autopilot.automaticTakeOff(getApplication());

        Uri url = getIntent().getData();
        if (url == null) {
            Logger.warn("User URI null, unable to process link.");
            finish();
            return;
        }

        liveData.observe(this, result -> {
            if (result.exception != null || result.uri == null) {
                finish();
            } else {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, result.uri);
                startActivity(browserIntent);
            }
        });

        resolveWalletUrl(url);
    }

    private void resolveWalletUrl(@NonNull final Uri url) {
        AirshipExecutors.threadPoolExecutor().submit(() -> {
            try {
                Logger.debug("Runner starting");

                RequestSession session = UAirship.shared().getRuntimeConfig().getRequestSession();

                Request request = new Request(
                        url,
                        "GET",
                        false
                );

                Response<String> response = session.execute(request, (status, responseHeaders, responseBody) -> {
                    if (UAHttpStatusUtil.inRedirectionRange(status)) {
                        return responseHeaders.get("Location");
                    }
                    return null;
                });

                if (response.getResult() != null) {
                    liveData.postValue(new Result(Uri.parse(response.getResult()), null));
                } else {
                    Logger.warn("No result found for Wallet URL, finishing action.");
                    liveData.postValue(new Result(null, null));
                }
            } catch (RequestException e) {
                liveData.postValue(new Result(null , e));
            }
        });
    }

    private static class Result {
        Uri uri;
        Exception exception;

        public Result(Uri uri, Exception exception) {
            this.uri = uri;
            this.exception = exception;
        }
    }
}
