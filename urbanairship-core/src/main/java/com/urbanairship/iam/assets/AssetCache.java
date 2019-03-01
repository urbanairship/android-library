/* Copyright Urban Airship and Contributors */

package com.urbanairship.iam.assets;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Asset cache.
 */
class AssetCache {

    private static final String CACHE_DIRECTORY = "com.urbanairship.iam.assets";
    private final File storageDirectory;
    private final StorageManager storageManager;

    /**
     * Default constructor.
     *
     * @param context The context.
     */
    AssetCache(@NonNull Context context) {
        this.storageDirectory = new File(context.getCacheDir(), CACHE_DIRECTORY);
        this.storageManager = findStorageManager(context);
    }

    /**
     * Gets assets for the given schedule ID.
     *
     * @param scheduleId The schedule ID.
     * @return The schedule's assets.
     */
    @WorkerThread
    @NonNull
    Assets getAssets(@NonNull String scheduleId) {
        if (!storageDirectory.exists()) {
            if (!storageDirectory.mkdirs()) {
                Logger.error("Failed to create asset storage directory.");
            }
        }

        File assetDirectory = new File(storageDirectory, scheduleId);
        if (!assetDirectory.exists()) {
            if (!assetDirectory.mkdirs()) {
                Logger.error("Failed to create assets directory.");
            }
        }

        return Assets.load(getAssetsDirectory(scheduleId));
    }

    /**
     * Clears any stored assets for the schedule ID.
     *
     * @param scheduleId The schedule ID.
     */
    @WorkerThread
    void clearAssets(@NonNull String scheduleId) {
        FileUtils.deleteRecursively(getAssetsDirectory(scheduleId));
    }

    /**
     * Helper method to get the schedule's asset directory for a given
     * schedule ID.
     *
     * @param scheduleId The schedule ID.
     * @return The asset directory.
     */
    @NonNull
    private File getAssetsDirectory(@NonNull String scheduleId) {
        if (!storageDirectory.exists()) {
            if (!storageDirectory.mkdirs()) {
                Logger.error("Failed to create asset storage directory.");
            }
        }

        File assetDirectory = new File(storageDirectory, scheduleId);
        if (!assetDirectory.exists()) {
            if (!assetDirectory.mkdirs()) {
                Logger.error("Failed to create assets directory.");
            }
        }

        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (assetDirectory.exists()) {
                try {
                    storageManager.setCacheBehaviorGroup(assetDirectory, true);
                } catch (IOException e) {
                    Logger.error(e, "Failed to set cache behavior on directory: %s", assetDirectory.getAbsoluteFile());
                }
            }
        }

        return assetDirectory;
    }

    @Nullable
    private static StorageManager findStorageManager(@NonNull Context context) {
        try {
            return (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }

}
