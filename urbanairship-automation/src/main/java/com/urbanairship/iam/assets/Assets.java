/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.storage.StorageManager;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Assets for an in-app message. All assets are stored on disk
 * in the app's cache directory.
 */
public class Assets implements Parcelable {

    /**
     * @hide
     */
    @NonNull
    public static final Parcelable.Creator<Assets> CREATOR = new Parcelable.Creator<Assets>() {

        @NonNull
        @Override
        public Assets createFromParcel(@NonNull Parcel in) {
            JsonMap parsedMap;
            try {
                parsedMap = JsonValue.parseString(in.readString()).optMap();
            } catch (JsonException e) {
                Logger.error(e, "Failed to parse metadata");
                parsedMap = JsonMap.EMPTY_MAP;
            }

            return new Assets(new File(in.readString()), parsedMap);
        }

        @NonNull
        @Override
        public Assets[] newArray(int size) {
            return new Assets[size];
        }
    };

    /**
     * The metadata file.
     */
    private static final String METADATA_FILE = "metadata";

    /**
     * The file store directory.
     */
    private static final String FILES_DIRECTORY = "files";

    private final Executor executor;
    private final File rootDirectory;
    private final File filesDirectory;
    private final File metadataFile;

    private final Map<String, JsonValue> metadata;
    private final Object metadataLock = new Object();

    /**
     * Loads assets from a directory.
     *
     * @param root The assets' root directory.
     * @return The assets.
     */
    @WorkerThread
    @NonNull
    static Assets load(@NonNull File root) {
        File metadata = new File(root, METADATA_FILE);
        return new Assets(root, readJson(metadata).optMap());
    }

    /**
     * Default constructor.
     *
     * @param root The assets' root directory.
     * @param metadata The metadata.
     */
    private Assets(@NonNull File root, @NonNull JsonMap metadata) {
        this.rootDirectory = root;
        this.filesDirectory = new File(root, FILES_DIRECTORY);
        this.metadataFile = new File(root, METADATA_FILE);
        this.metadata = new HashMap<>(metadata.getMap());
        this.executor = AirshipExecutors.newSerialExecutor();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        synchronized (metadataLock) {
            dest.writeString(JsonValue.wrapOpt(metadata).toString());
        }
        dest.writeString(rootDirectory.getAbsolutePath());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Gets the file for the asset for the given key.
     *
     * @param key The key.
     * @return The asset's file.
     */
    @NonNull
    public File file(@NonNull String key) {
        prepareDirectory();
        return new File(filesDirectory, UAStringUtil.sha256(key));
    }

    /**
     * Gets metadata for a key.
     *
     * @param key The key.
     * @return The metadata for the key.
     */
    @NonNull
    public JsonValue getMetadata(@NonNull String key) {
        synchronized (metadataLock) {
            JsonValue value = metadata.get(key);
            return value == null ? JsonValue.NULL : value;
        }
    }

    /**
     * Sets metadata.
     *
     * @param key The key.
     * @param value The value.
     */
    public void setMetadata(@NonNull String key, @NonNull JsonSerializable value) {
        synchronized (metadataLock) {
            metadata.put(key, value.toJsonValue());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    writeJson(metadataFile, JsonValue.wrapOpt(metadata));
                }
            });
        }
    }

    /**
     * Writes JSON to a file.
     *
     * @param file The file.
     * @param jsonValue The JSON.
     */
    @WorkerThread
    private void writeJson(@NonNull File file, @NonNull JsonValue jsonValue) {
        prepareDirectory();

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(jsonValue.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
            Logger.error(e, "Failed to write metadata.");
        } finally {
            closeQuietly(outputStream);
        }
    }

    /**
     * Helper method to read JSON from a file.
     *
     * @param file The file.
     * @return The JSON from the file.
     */
    private static JsonValue readJson(File file) {
        if (!file.exists()) {
            return JsonValue.NULL;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringWriter writer = new StringWriter();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
            return JsonValue.parseString(writer.toString());
        } catch (IOException e) {
            Logger.error(e, "Error reading file");
        } catch (JsonException e) {
            Logger.error(e, "Error parsing file as JSON.");
        } finally {
            closeQuietly(reader);
        }

        return JsonValue.NULL;
    }

    /**
     * Attempts to close a closeable.
     *
     * @param closeable The closeable.
     */
    private static void closeQuietly(@Nullable Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Logger.error(e);
            }
        }
    }

    /**
     * Prepares the asset's directory for usage.
     */
    private void prepareDirectory() {
        if (!rootDirectory.exists()) {
            if (rootDirectory.mkdirs()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        StorageManager storageManager = (StorageManager) UAirship.getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
                        storageManager.setCacheBehaviorGroup(rootDirectory, true);
                    } catch (IOException e) {
                        Logger.error(e, "Failed to set cache behavior on directory: %s", rootDirectory.getAbsoluteFile());
                    }
                }
            } else {
                Logger.error("Failed to create assets directory.");
            }
        }

        if (!filesDirectory.exists() && !filesDirectory.mkdirs()) {
            Logger.error("Failed to create directory: %s", filesDirectory.getAbsoluteFile());
        }
    }

}
