/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Callable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * Manages assets for in-app messages.
 */
public class AssetManager {

    @IntDef({ PREPARE_RESULT_OK, PREPARE_RESULT_RETRY, PREPARE_RESULT_CANCEL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PrepareResult {}

    /**
     * Indicates a successful result.
     */
    public static final int PREPARE_RESULT_OK = 0;

    /***
     * Indicates that the the prepare step should be retried.
     */
    public static final int PREPARE_RESULT_RETRY = 1;

    /***
     * Indicates that the prepare step failed and the schedule should be canceled.
     */
    public static final int PREPARE_RESULT_CANCEL = 2;

    @Nullable
    private PrepareAssetsDelegate assetsDelegate;

    @Nullable
    private CachePolicyDelegate cachePolicyDelegate;

    @NonNull
    private final AssetCache assetCache;

    /**
     * Default constructor. Applications should not create their own, instead use the asset manager
     * from {@link InAppMessageManager#getAssetManager()}
     *
     * @param context The application context.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AssetManager(@NonNull Context context) {
        this.assetCache = new AssetCache(context);
        this.assetsDelegate = new AirshipPrepareAssetsDelegate();
    }

    @VisibleForTesting
    AssetManager(@NonNull AssetCache assetCache) {
        this.assetCache = assetCache;
    }

    /**
     * Sets the prepare assets delegate. The delegate is used to populate an {@link Assets} instance
     * for a schedule.
     * <p>
     * To preserve Airship default behavior, extend {@link AirshipPrepareAssetsDelegate}
     * and call through to the super's method.
     *
     * @param assetsDelegate The asset delegate.
     */
    public void setPrepareAssetDelegate(@Nullable PrepareAssetsDelegate assetsDelegate) {
        this.assetsDelegate = assetsDelegate;
    }

    /**
     * Sets the cache policy delegate.
     *
     * @param cachePolicyDelegate The cache policy delegate.
     */
    public void setCachePolicyDelegate(@Nullable CachePolicyDelegate cachePolicyDelegate) {
        this.cachePolicyDelegate = cachePolicyDelegate;
    }

    /**
     * Called when a new schedule is available.
     *
     * @param scheduleId The schedule ID.
     * @param extendedMessageCallable Callback used to get the extended message.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @WorkerThread
    public void onSchedule(@NonNull String scheduleId, @NonNull Callable<InAppMessage> extendedMessageCallable) {
        CachePolicyDelegate cachePolicyDelegate = this.cachePolicyDelegate;
        PrepareAssetsDelegate assetsDelegate = this.assetsDelegate;

        if (cachePolicyDelegate != null && assetsDelegate != null) {
            try {
                InAppMessage message = extendedMessageCallable.call();
                if (cachePolicyDelegate.shouldCacheOnSchedule(scheduleId, message)) {
                    Assets assets = assetCache.getAssets(scheduleId);
                    assetsDelegate.onSchedule(scheduleId, message, assets);
                    assetCache.releaseAssets(scheduleId, false);
                }
            } catch (Exception e) {
                Logger.error(e, "Unable to prepare assets for schedule: %s", scheduleId);
            }
        }
    }

    /**
     * Called when a schedule needs to be prepared.
     *
     * @param scheduleId The schedule ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @WorkerThread
    @PrepareResult
    public int onPrepare(@NonNull String scheduleId, @NonNull InAppMessage message) {
        PrepareAssetsDelegate assetsDelegate = this.assetsDelegate;
        if (assetsDelegate != null) {
            Assets assets = assetCache.getAssets(scheduleId);
            return assetsDelegate.onPrepare(scheduleId, message, assets);
        }

        return PREPARE_RESULT_OK;
    }

    /**
     * Called when the schedule's message finished displaying.
     *
     * @param scheduleId The schedule ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @WorkerThread
    public void onDisplayFinished(@NonNull String scheduleId, @NonNull InAppMessage inAppMessage) {
        CachePolicyDelegate cachePolicyDelegate = this.cachePolicyDelegate;
        boolean delete = false;
        if (cachePolicyDelegate == null || !cachePolicyDelegate.shouldPersistCacheAfterDisplay(scheduleId, inAppMessage)) {
            delete = true;
        }

        assetCache.releaseAssets(scheduleId, delete);
    }

    /**
     * Called when a schedule is finished.
     *
     * @param scheduleId The schedule.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @WorkerThread
    public void onFinish(@NonNull String scheduleId) {
        assetCache.releaseAssets(scheduleId, true);
    }

    /**
     * Gets the assets for a schedule.
     *
     * @param scheduleId The schedule ID.
     * @return The assets.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public Assets getAssets(@NonNull String scheduleId) {
        return assetCache.getAssets(scheduleId);
    }

}
