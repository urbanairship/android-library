package com.urbanairship.actions;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

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

public class LoadingActivity extends AppCompatActivity {

    private URL url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        try {
            Uri uri = getIntent().getData();
            if (uri != null) {
                url = new URL(getIntent().getData().toString());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            finish();
        }

        if (url == null) {
            Logger.warn("User URI null, unable to process link.");
            finish();
        }

        AirshipExecutors.THREAD_POOL_EXECUTOR.execute(new Runnable() {

            private int maxRetries = 5;

            @Override
            public void run() {
                try {
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
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(response.getResponseHeader("Location")));
                        startActivity(browserIntent);
                    } else {
                        Logger.warn("No redirection found for the Wallet URL. Finishing action.");
                        finish();
                    }
                } catch (RequestException e) {
                    maxRetries--;
                    if (maxRetries > 0) {
                        Logger.warn("Wallet action request error, trying again, tries left : " + maxRetries);
                        AirshipExecutors.THREAD_POOL_EXECUTOR.submit(this);
                    } else {
                        AirshipExecutors.THREAD_POOL_EXECUTOR.shutdown();
                        finish();
                    }
                }
            }
        });
    }
}
