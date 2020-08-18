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
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                url = new URL(getIntent().getExtras().getString("url"));
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            finish();
        }

        if (url == null) {
            Logger.debug("User URI null, unable to process link.");
            Toast.makeText(this, "URL got from the push is null. Going back.", Toast.LENGTH_SHORT).show();
            finish();
        }

        try {
            //Get the real Google Pay URL and then navigate to it
            HttpURLConnection httpURLConnection;
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setInstanceFollowRedirects(false);
            URL secondURL = new URL(httpURLConnection.getHeaderField("Location"));

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(secondURL.toString()));
            startActivity(browserIntent);
            finish();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}