package com.urbanairship.push.hms;

import android.content.Context;

import com.urbanairship.UALog;
import com.urbanairship.util.UAStringUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

/**
 * Caches the token to allow us to use the same access pattern for all versions of HMS devices.
 *
 * `HmsInstanceId#getToken` only returns the token if the EMUI version of the devices is 10 or newer.
 * Otherwise we have to rely on `HmsMessageService#onNewToken`. Since our SDK relies on a way to get
 * the token every time using some sort of `getToken` method, we cache the token locally and return it
 * in {@link HmsPushProvider#getRegistrationToken(Context)} if `HmsInstanceId#getToken` fails to return
 * a token.
 */
class HmsTokenCache {

    private static final String FILE_PATH = "com.urbanairship.push.hms/token.txt";

    private final Object lock = new Object();
    private String token;

    private static HmsTokenCache instance = new HmsTokenCache();

    public static HmsTokenCache shared() {
        return instance;
    }

    @VisibleForTesting
    HmsTokenCache() {}

    @Nullable
    public String get(@NonNull Context context) {
        synchronized (lock) {
            if (token == null) {
                token = readToken(context);
                UALog.v("HMS token from cache: " + token);
            }
            return token;
        }
    }

    public void set(@NonNull Context context, @Nullable String token) {
        synchronized (lock) {
            if (UAStringUtil.equals(token, get(context))) {
                return;
            }

            this.token = token;

            if (token != null) {
                writeToken(context, token);
                UALog.v("Cached HMS token %s", token);
            } else {
                deleteToken(context);
                UALog.v("Deleted cached HMS token");
            }
        }
    }

    private String readToken(@NonNull Context context) {

        File file = new File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH);
        if (!file.exists()) {
            return null;
        }

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            inputStream.read(data);
            String token = new String(data, "UTF-8");
            return token;
        } catch (Exception e) {
            UALog.e(e, "Failed to read HMS token");
        } finally {
            closeQuietly(inputStream);
        }

        return null;
    }

    private void writeToken(@NonNull Context context, @NonNull String token) {
        File file = new File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH);
        File parent = file.getParentFile();

        if (parent == null || !parent.exists() && !parent.mkdirs()) {
            UALog.w("Unable to create HMS token cache.");
            return;
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(token.getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            UALog.e(e, "Failed to write HMS token.");
        } finally {
            closeQuietly(fileOutputStream);
        }
    }

    private void closeQuietly(Closeable outputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                UALog.e(e, "Failed to close stream.");
            }
        }
    }

    private void deleteToken(@NonNull Context context) {
        File file = new File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH);
        if (file.exists() && !file.delete()) {
            UALog.e("Failed to delete HMS token cache.");
        }
    }

}
