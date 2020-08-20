package com.urbanairship.actions;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.urbanairship.Logger;
import com.urbanairship.R;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

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

        try {
            // Get the real Google Pay URL and then navigate to it
            HttpURLConnection httpURLConnection;
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setInstanceFollowRedirects(false);
            String redirectURL = httpURLConnection.getHeaderField("Location");

            if (redirectURL != null) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectURL));
                startActivity(browserIntent);
            } else {
                Logger.warn("No redirection found for the Wallet URL. Finishing action.");
            }

            finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
