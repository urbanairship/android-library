package com.urbanairship.images;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.http.HttpResponseCache;
import android.util.LruCache;

import com.urbanairship.Logger;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

class ImageCache {

    private static final String CACHE_DIR = "urbanairship-cache";

    /**
     * Max amount of memory cache.
     */
    private static final int MAX_MEM_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    /**
     * Max file size before we cache in memory.
     */
    private static final int MAX_MEM_CACHE_FILE_SIZE = 1024 * 1024; // 1MB

    /**
     * Disk cache size.
     */
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 50; // 50MB

    private final LruCache<String, CacheEntry> memoryCache;

    private final Context context;

    ImageCache(@NonNull Context context) {
        this.context = context.getApplicationContext();

        // Memory Cache - 1/8 the available memory. Taken from https://developer.android.com/topic/performance/graphics/cache-bitmap
        int memCacheSize = (int) Math.min(MAX_MEM_CACHE_SIZE, Runtime.getRuntime().maxMemory() / 8);

        this.memoryCache = new LruCache<String, CacheEntry>(memCacheSize) {
            @Override
            protected int sizeOf(String key, @NonNull CacheEntry entry) {
                if (entry.byteCount > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return (int) entry.byteCount;
            }
        };
    }

    @WorkerThread
    void cacheDrawable(@NonNull String key, @NonNull Drawable drawable, long byteCount) {
        if (byteCount <= MAX_MEM_CACHE_FILE_SIZE) {
            memoryCache.put(key, new CacheEntry(drawable, byteCount));
        }
    }

    @Nullable
    Drawable getDrawable(@NonNull String key) {
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) {
            return null;
        }
        return entry.drawable;
    }

    /**
     * Installs a HttpResponseCache if a cache is not already installed.
     */
    @WorkerThread
    void installHttpCache() {
        // URL Cache
        File cacheDir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Logger.error("Failed to create the cache.");
        }

        if (HttpResponseCache.getInstalled() == null) {
            try {
                HttpResponseCache.install(cacheDir, DISK_CACHE_SIZE);
            } catch (IOException e) {
                Logger.error("Unable to install image loader cache");
            }
        }
    }

    private static class CacheEntry {

        private final long byteCount;
        private final Drawable drawable;

        CacheEntry(@NonNull Drawable drawable, long byteCount) {
            this.drawable = drawable;
            this.byteCount = byteCount;
        }

    }

}
