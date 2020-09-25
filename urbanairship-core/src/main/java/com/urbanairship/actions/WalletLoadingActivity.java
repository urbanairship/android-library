package com.urbanairship.actions;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.util.UAHttpStatusUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class WalletLoadingActivity extends AppCompatActivity {

    private URL url;
    private final MutableLiveData<Result> liveData = new MutableLiveData<>();
    private int maxRetries = 5;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ua_activity_wallet_loading);

        try {
            Uri uri = getIntent().getData();
            if (uri != null) {
                url = new URL(getIntent().getData().toString());
            }
        } catch (MalformedURLException e) {
            Logger.warn("The wallet URL is incorrect, finishing operation.", e);
            finish();
            return;
        }

        if (url == null) {
            Logger.warn("User URI null, unable to process link.");
            finish();
            return;
        }

        liveData.observe(this, new Observer<Result>() {
            @Override
            public void onChanged(Result result) {
                if (result.exception != null) {
                    if (maxRetries > 0) {
                        if (handler != null)
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    maxRetries--;
                                    Logger.warn("Wallet action request error, trying again in 10s, tries left : " + maxRetries);
                                    resolveWalletUrl();
                                }
                            }, 10000);
                    } else {
                        finish();
                    }
                } else {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, result.uri);
                    startActivity(browserIntent);
                }
            }
        });
        resolveWalletUrl();
    }

    private void resolveWalletUrl() {
        AirshipExecutors.THREAD_POOL_EXECUTOR.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    Logger.debug("Runner starting");
                    Response<String> response = new Request()
                            .setOperation("GET", url)
                            .setInstanceFollowRedirects(false)
                            .execute(new ResponseParser<String>() {
                                @Override
                                public String parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) {
                                    if (UAHttpStatusUtil.inRedirectionRange(status)) {
                                        if (headers != null && headers.get("Location") != null) {
                                            return headers.get("Location").get(0);
                                        }
                                    }
                                    return null;
                                }
                            });
                    if (response.getResult() != null) {
                        liveData.postValue(new Result(Uri.parse(response.getResponseHeader("Location")), null));
                    } else {
                        Logger.warn("No result found for Wallet URL, finishing action.");
                        finish();
                    }
                } catch (RequestException e) {
                    liveData.postValue(new Result(null , e));
                }
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
