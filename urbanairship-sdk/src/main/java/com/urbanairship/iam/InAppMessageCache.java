package com.urbanairship.iam;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.urbanairship.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Cache for an in-app message. The cache will automatically be deleted when the in-app message finishes.
 */
public class InAppMessageCache implements Parcelable {

    public static final Creator<InAppMessageCache> CREATOR = new Creator<InAppMessageCache>() {
        @Override
        public InAppMessageCache createFromParcel(Parcel in) {
            return new InAppMessageCache(in);
        }

        @Override
        public InAppMessageCache[] newArray(int size) {
            return new InAppMessageCache[size];
        }
    };


    /**
     * Cache key for the in-app message media.
     */
    public static final String MEDIA_CACHE_KEY = "MEDIA_CACHE_KEY";

    /**
     * Cache key for the image width.
     */
    public static final String IMAGE_WIDTH_CACHE_KEY = "width";

    /**
     * Cache key for the image height.
     */
    public static final String IMAGE_HEIGHT_CACHE_KEY = "height";

    private static final String CACHE_DIRECTORY = "com.urbanairship.iam";
    private static boolean isParentReady = false;

    private final File directory;
    private final Bundle assets;

    /**
     * Default constructor.
     *
     * @param directory The directory.
     */
    private InAppMessageCache(@NonNull File directory) {
        this.directory = directory;
        this.assets = new Bundle();
    }

    /**
     * Constructor for parcelable implementation.
     *
     * @param in The parcel.
     */
    private InAppMessageCache(Parcel in) {
        assets = in.readBundle(getClass().getClassLoader());
        directory = new File(in.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(assets);
        dest.writeString(directory.getAbsolutePath());
    }


    @Override
    public int describeContents() {
        return 0;
    }


    /**
     * Creates a new file instance in the cache directory for this in-app message.
     *
     * @param fileName The file name.
     * @return The created file.
     */
    public File file(String fileName) {
        return new File(directory, fileName);
    }

    /**
     * A bundle that can be used to store assets for the in-app message.
     *
     * @return The bundle.
     */
    public Bundle getBundle() {
        return assets;
    }


    /**
     * Deletes the cache.
     *
     * @return {@code true} if the cache was deleted, otherwise {@code false}.
     */
    public boolean delete() {
        assets.clear();
        return directory.delete();
    }

    /**
     * Factory method.
     *
     * @return An cache instance.
     * @throws java.io.IOException if the cache directory was not able to be created.
     */
    @WorkerThread
    public static InAppMessageCache newCache(Context context, InAppMessage message) throws IOException {
        File parent;
        synchronized (CACHE_DIRECTORY) {
            parent = new File(context.getCacheDir(), CACHE_DIRECTORY);
            if (!isParentReady) {
                if (parent.exists()) {
                    FileUtils.deleteRecursively(parent);
                }

                if (parent.exists() || parent.mkdirs()) {
                    isParentReady = true;
                } else {
                    throw new IOException("Unable to create cache directory");
                }
            }
        }

        File cacheDir = new File(parent, message.getId());
        int i = 0;
        while (cacheDir.exists()) {
            cacheDir = new File(parent, message.getId() + " " + i);
            i++;
        }

        if (!cacheDir.mkdirs()) {
            throw new IOException("Unable to create cache.");
        }

        return new InAppMessageCache(cacheDir);
    }

}
