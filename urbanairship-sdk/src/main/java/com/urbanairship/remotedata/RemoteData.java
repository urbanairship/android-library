/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.reactive.BiFunction;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Supplier;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * RemoteData top-level class.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteData extends AirshipComponent {

    /**
     * The remote data store.
     */
    private static final String DATABASE_NAME = "ua_remotedata.db";

    /**
     * The key for getting and setting the last modified timestamp from the preference datastore.
     */
    private static final String LAST_MODIFIED_KEY = "com.urbanairship.remotedata.LAST_MODIFIED";

    private Context context;
    private AirshipConfigOptions configOptions;
    private JobDispatcher jobDispatcher;
    private RemoteDataJobHandler jobHandler;
    private PreferenceDataStore preferenceDataStore;
    private Handler backgroundHandler;
    private ActivityMonitor activityMonitor;

    private final ActivityMonitor.Listener activityListener = new ActivityMonitor.SimpleListener() {
        @Override
        public void onForeground(long time) {
           RemoteData.this.refresh();
        }
    };

    @VisibleForTesting
    Subject<List<RemoteDataPayload>> payloadUpdates;

    @VisibleForTesting
    HandlerThread backgroundThread;

    @VisibleForTesting
    RemoteDataStore dataStore;

    /**
     * RemoteData constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The preference data store
     * @param configOptions The config options.
     * @param activityMonitor The activity monitor.
     */
    public RemoteData(Context context, @NonNull PreferenceDataStore preferenceDataStore, AirshipConfigOptions configOptions, ActivityMonitor activityMonitor) {
        this(context, preferenceDataStore, configOptions, activityMonitor, JobDispatcher.shared(context));
    }

    /**
     * RemoteData constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The preference data store
     * @param configOptions The config options.
     * @param activityMonitor The activity monitor.
     * @param dispatcher A job dispatcher.
     */
    @VisibleForTesting
    RemoteData(Context context, @NonNull PreferenceDataStore preferenceDataStore, AirshipConfigOptions configOptions, ActivityMonitor activityMonitor, JobDispatcher dispatcher) {
        this.context = context;
        this.configOptions = configOptions;
        this.jobDispatcher = dispatcher;
        this.dataStore = new RemoteDataStore(context, configOptions.getAppKey(), DATABASE_NAME);
        this.preferenceDataStore = preferenceDataStore;
        this.backgroundThread = new HandlerThread("remote data store");
        this.payloadUpdates = Subject.create();
        this.activityMonitor = activityMonitor;
    }

    @Override
    protected void init() {
        start();
        activityMonitor.addListener(activityListener);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(activityListener);
        stop();
    }

    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (jobHandler == null) {
            jobHandler = new RemoteDataJobHandler(context, airship);
        }

        return jobHandler.performJob(jobInfo);
    }

    /**
     * Refreshes the remote data from the cloud
     */
    public void refresh() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(RemoteDataJobHandler.ACTION_REFRESH)
                                 .setId(JobInfo.REMOTE_DATA_REFRESH)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(RemoteData.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * Produces an Observable of RemoteDataPayload tied to a specific type.
     * Subscribers will be notified of any cached data upon subscription, as well as any subsequent changes
     * following refresh updates, provided the timestamp is fresh.
     *
     * @param type The payload type.
     * @return An Observable of RemoteDataPayload.
     */
    public Observable<RemoteDataPayload> payloadsForType(final @NonNull String type) {
        return allPayloadsForType(type).distinctUntilChanged();
    }

    /**
     * Produces an Observable of a List of RemoteDataPayload objects corresponding to the provided types.
     * Subscribers will be notified of any cached data upon subscription, as well as subsequent changes
     * following refresh updates, provided one of the payload's timestamps is fresh.
     *
     * @param type A desired type
     * @param type Another desired type
     * @return An Observable of RemoteDataPayload.
     */
    public Observable<Collection<RemoteDataPayload>> payloadsForTypes(final String type, final String otherType) {
        return allPayloadsForTypes(type, otherType).distinctUntilChanged();
    }

    /**
     * Spins up the background thread and handler.
     */
    void start() {
        backgroundThread.start();
        backgroundHandler = new Handler(this.backgroundThread.getLooper());
    }

    /**
     * Winds down the background thread and handler.
     */
    void stop() {
        backgroundThread.quit();
    }

    /**
     * Refresh response callback for use from the RemoteDataJobHandler.
     *
     * @param newPayloads A list of new payloads.
     */
    @WorkerThread
    void handleRefreshResponse(final List<RemoteDataPayload> newPayloads) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                overwriteCachedData(newPayloads);
                notifySubscribers(newPayloads);
            }
        });
    }

    /**
     * Saves the last modified timestamp received in a refresh request.
     *
     * @param lastModified The timestamp in ISO-8601 format.
     */
    @WorkerThread void setLastModified(String lastModified) {
        preferenceDataStore.put(LAST_MODIFIED_KEY, lastModified);
    }

    /**
     * Retrieves the mostly recent last modified timestamp received from the server.
     *
     * @return A timestamp in ISO-8601 format, or <code>null</code> if one has not been received.
     */
    @WorkerThread String getLastModified() {
        return preferenceDataStore.getString(LAST_MODIFIED_KEY, null);
    }

    private Observable<RemoteDataPayload> allPayloadsForType(final @NonNull String type) {
        Observable<RemoteDataPayload> cached = cachedPayloads().defaultIfEmpty(new RemoteDataPayload(type, 0, JsonMap.newBuilder().build()));
        Observable<RemoteDataPayload> updated = payloadUpdates.map(new Function<List<RemoteDataPayload>, RemoteDataPayload>() {
            @Override
            public RemoteDataPayload apply(List<RemoteDataPayload> payloads) {
                for (RemoteDataPayload payload : payloads) {
                    if (payload.getType().equals(type)) {
                        return payload;
                    }
                }
                return new RemoteDataPayload(type, 0, JsonMap.newBuilder().build());
            }
        });

        return Observable.concat(cached, updated).filter(new Predicate<RemoteDataPayload>() {
            @Override
            public boolean apply(RemoteDataPayload payload) {
                return payload.getType().equals(type);
            }
        });
    }

    private Observable<Collection<RemoteDataPayload>> allPayloadsForTypes(final String type, final String otherType) {
        return Observable.zip(allPayloadsForType(type), allPayloadsForType(otherType),
                new BiFunction<RemoteDataPayload, RemoteDataPayload, Collection<RemoteDataPayload>>() {
                    @Override
                    public Collection<RemoteDataPayload> apply(RemoteDataPayload payload, RemoteDataPayload otherPayload) {
                        return Arrays.asList(payload, otherPayload);
                    }
        });
    }

    /**
     * Produces an Observable of RemoteDataPayload drawn from the cache.
     * Subscription side effects are implicitly tied to the background thread.
     *
     * @return An Observable of RemoteDataPayload.
     */
    private Observable<RemoteDataPayload> cachedPayloads() {
        return Observable.defer(new Supplier<Observable<RemoteDataPayload>>() {
            @Override
            public Observable<RemoteDataPayload> apply() {
                return Observable.from(dataStore.getPayloads()).subscribeOn(Schedulers.looper(backgroundHandler.getLooper()));
            }
        });
    }

    @WorkerThread
    private void notifySubscribers(final List<RemoteDataPayload> newPayloads) {
        payloadUpdates.onNext(newPayloads);
    }

    @WorkerThread
    private void overwriteCachedData(final List<RemoteDataPayload> newPayloads) {
        // Clear the cache
        if (!dataStore.deletePayloads()) {
            Logger.error("Unable to delete existing payload data");
            return;
        }
        // If successful, save the new payload data.
        if (!dataStore.savePayloads(newPayloads)) {
            Logger.error("Unable to save remote data payloads");
        }
    }
}
