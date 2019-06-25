/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.locale.LocaleChangedListener;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Supplier;
import com.urbanairship.util.AirshipHandlerThread;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /**
     * The key for getting and setting the foreground refresh interval from the preference datastore.
     */
    private static final String FOREGROUND_REFRESH_INTERVAL_KEY = "com.urbanairship.remotedata.FOREGROUND_REFRESH_INTERVAL";

    /**
     * The key for getting and setting the last refresh time from the preference datastore.
     */
    private static final String LAST_REFRESH_TIME_KEY = "com.urbanairship.remotedata.LAST_REFRESH_TIME";

    /**
     * The key for getting and setting the last refresh metadata.
     */
    private static final String LAST_REFRESH_METADATA = "com.urbanairship.remotedata.LAST_REFRESH_METADATA";

    /**
     * The key for getting and setting the app version of the last refresh from the preference datastore.
     */
    private static final String LAST_REFRESH_APP_VERSION_KEY = "com.urbanairship.remotedata.LAST_REFRESH_APP_VERSION";

    private final JobDispatcher jobDispatcher;
    private final LocaleManager localeManager;
    private RemoteDataJobHandler jobHandler;
    private final PreferenceDataStore preferenceDataStore;
    private Handler backgroundHandler;
    private final ActivityMonitor activityMonitor;

    private final ApplicationListener applicationListener = new SimpleApplicationListener() {
        @Override
        public void onForeground(long time) {
            RemoteData.this.onForeground();
        }
    };

    @VisibleForTesting
    final
    Subject<Set<RemoteDataPayload>> payloadUpdates;

    @VisibleForTesting
    final
    HandlerThread backgroundThread;

    @VisibleForTesting
    final
    RemoteDataStore dataStore;

    /**
     * RemoteData constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The preference data store
     * @param configOptions The config options.
     * @param activityMonitor The activity monitor.
     */
    public RemoteData(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                      @NonNull AirshipConfigOptions configOptions, @NonNull ActivityMonitor activityMonitor) {
        this(context, preferenceDataStore, configOptions, activityMonitor,
                JobDispatcher.shared(context), LocaleManager.shared(context));
    }

    /**
     * RemoteData constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The preference data store
     * @param activityMonitor The activity monitor.
     * @param dispatcher The job dispatcher.
     * @param localeManager The locale manager.
     */
    @VisibleForTesting
    RemoteData(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
               @NonNull AirshipConfigOptions configOptions, @NonNull ActivityMonitor activityMonitor,
               @NonNull JobDispatcher dispatcher, @NonNull LocaleManager localeManager) {
        super(context, preferenceDataStore);
        this.jobDispatcher = dispatcher;
        this.dataStore = new RemoteDataStore(context, configOptions.appKey, DATABASE_NAME);
        this.preferenceDataStore = preferenceDataStore;
        this.backgroundThread = new AirshipHandlerThread("remote data store");
        this.payloadUpdates = Subject.create();
        this.activityMonitor = activityMonitor;
        this.localeManager = localeManager;
    }

    @Override
    protected void init() {
        super.init();
        backgroundThread.start();
        backgroundHandler = new Handler(this.backgroundThread.getLooper());
        activityMonitor.addApplicationListener(applicationListener);

        localeManager.addListener(new LocaleChangedListener() {
            @Override
            public void onLocaleChanged(@NonNull Locale locale) {
                if (shouldRefresh()) {
                    refresh();
                }
            }
        });

        if (shouldRefresh()) {
            refresh();
        }
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(applicationListener);
        backgroundThread.quit();
    }

    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (jobHandler == null) {
            jobHandler = new RemoteDataJobHandler(getContext(), airship);
        }

        return jobHandler.performJob(jobInfo);
    }

    /**
     * Called when the application is foregrounded.
     */
    private void onForeground() {
        long timeSinceLastRefresh = System.currentTimeMillis() - preferenceDataStore.getLong(LAST_REFRESH_TIME_KEY, -1);
        if (getForegroundRefreshInterval() <= timeSinceLastRefresh || shouldRefresh()) {
            refresh();
        }
    }

    /**
     * Sets the foreground refresh interval.
     *
     * @param milliseconds The foreground refresh interval.
     */
    public void setForegroundRefreshInterval(long milliseconds) {
        preferenceDataStore.put(FOREGROUND_REFRESH_INTERVAL_KEY, milliseconds);
    }

    /**
     * Gets the foreground refresh interval.
     *
     * @return The foreground refresh interval.
     */
    public long getForegroundRefreshInterval() {
        return preferenceDataStore.getLong(FOREGROUND_REFRESH_INTERVAL_KEY, 0);
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
    @NonNull
    public Observable<RemoteDataPayload> payloadsForType(final @NonNull String type) {
        return payloadsForTypes(Collections.singleton(type)).flatMap(new Function<Collection<RemoteDataPayload>, Observable<RemoteDataPayload>>() {
            @NonNull
            @Override
            public Observable<RemoteDataPayload> apply(@NonNull Collection<RemoteDataPayload> payloads) {
                return Observable.from(payloads);
            }
        });
    }

    /**
     * Produces an Observable of a List of RemoteDataPayload objects corresponding to the provided types.
     * Subscribers will be notified of any cached data upon subscription, as well as subsequent changes
     * following refresh updates, provided one of the payload's timestamps is fresh.
     *
     * @param types Array of types.
     * @return An Observable of RemoteDataPayload.
     */
    @NonNull
    public Observable<Collection<RemoteDataPayload>> payloadsForTypes(@NonNull String... types) {
        return payloadsForTypes(Arrays.asList(types));
    }

    /**
     * Produces an Observable of a List of RemoteDataPayload objects corresponding to the provided types.
     * Subscribers will be notified of any cached data upon subscription, as well as subsequent changes
     * following refresh updates, provided one of the payload's timestamps is fresh.
     *
     * @param types A collection of types.
     * @return An Observable of RemoteDataPayload.
     */
    @NonNull
    public Observable<Collection<RemoteDataPayload>> payloadsForTypes(@NonNull final Collection<String> types) {
        return Observable.concat(cachedPayloads(types), payloadUpdates)
                         .map(new Function<Set<RemoteDataPayload>, Map<String, Collection<RemoteDataPayload>>>() {
                             @NonNull
                             @Override
                             public Map<String, Collection<RemoteDataPayload>> apply(@NonNull Set<RemoteDataPayload> payloads) {
                                 Map<String, Collection<RemoteDataPayload>> map = new HashMap<>();
                                 for (RemoteDataPayload payload : payloads) {
                                     Collection<RemoteDataPayload> mappedPayloads = map.get(payload.getType());
                                     if (mappedPayloads == null) {
                                         mappedPayloads = new HashSet<>();
                                         map.put(payload.getType(), mappedPayloads);
                                     }
                                     mappedPayloads.add(payload);
                                 }

                                 return map;
                             }
                         })
                         .map(new Function<Map<String, Collection<RemoteDataPayload>>, Collection<RemoteDataPayload>>() {
                             @NonNull
                             @Override
                             public Collection<RemoteDataPayload> apply(@NonNull Map<String, Collection<RemoteDataPayload>> payloadMap) {
                                 Set<RemoteDataPayload> payloads = new HashSet<>();
                                 for (String type : new HashSet<>(types)) {
                                     Collection<RemoteDataPayload> mappedPayloads = payloadMap.get(type);
                                     if (mappedPayloads != null) {
                                         payloads.addAll(mappedPayloads);
                                     } else {
                                         payloads.add(RemoteDataPayload.emptyPayload(type));
                                     }
                                 }

                                 return payloads;
                             }
                         })
                         .distinctUntilChanged();
    }

    /**
     * Retrieves the mostly recent last modified timestamp received from the server.
     *
     * @return A timestamp in RFC 1123 format, or <code>null</code> if one has not been received.
     */
    @Nullable
    String getLastModified() {
        if (isLastMetadataCurrent()) {
            return preferenceDataStore.getString(LAST_MODIFIED_KEY, null);
        } else {
            return null;
        }
    }

    public boolean shouldRefresh() {
        long timeSinceLastRefresh = System.currentTimeMillis() - preferenceDataStore.getLong(LAST_REFRESH_TIME_KEY, -1);
        if (getForegroundRefreshInterval() <= timeSinceLastRefresh) {
            return true;
        }

        int appVersion = preferenceDataStore.getInt(LAST_REFRESH_APP_VERSION_KEY, 0);
        PackageInfo packageInfo = UAirship.getPackageInfo();
        if (packageInfo != null && packageInfo.versionCode != appVersion) {
            return true;
        }

        if (!isLastMetadataCurrent()) {
            return true;
        }

        return false;
    }

    /**
     * Called when the job is finished refreshing the remote data.
     */
    @WorkerThread
    void onNewData(@NonNull final Set<RemoteDataPayload> payloads, final @Nullable String lastModified, final @NonNull JsonMap metadata) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                // Clear the cache
                if (!dataStore.deletePayloads()) {
                    Logger.error("Unable to delete existing payload data");
                    return;
                }
                // If successful, save the new payload data.
                if (!dataStore.savePayloads(payloads)) {
                    Logger.error("Unable to save remote data payloads");
                }

                preferenceDataStore.put(LAST_REFRESH_METADATA, metadata);
                preferenceDataStore.put(LAST_MODIFIED_KEY, lastModified);

                payloadUpdates.onNext(payloads);
            }
        });
    }

    @WorkerThread
    void onRefreshFinished() {
        preferenceDataStore.put(LAST_REFRESH_TIME_KEY, System.currentTimeMillis());

        PackageInfo packageInfo = UAirship.getPackageInfo();
        if (packageInfo != null) {
            preferenceDataStore.put(LAST_REFRESH_APP_VERSION_KEY, packageInfo.versionCode);
        }
    }

    /**
     * Produces an Observable of RemoteDataPayload drawn from the cache.
     * Subscription side effects are implicitly tied to the background thread.
     *
     * @param types The data types.
     * @return An Observable of RemoteDataPayload.
     */
    private Observable<Set<RemoteDataPayload>> cachedPayloads(final Collection<String> types) {
        return Observable.defer(new Supplier<Observable<Set<RemoteDataPayload>>>() {
            @NonNull
            @Override
            public Observable<Set<RemoteDataPayload>> apply() {
                return Observable.just(dataStore.getPayloads(types))
                                 .subscribeOn(Schedulers.looper(backgroundHandler.getLooper()));
            }
        });
    }

    /**
     * Creates the client metadata used to fetch the request.
     *
     * @param locale The locale.
     * @return The metadata map.
     */
    @NonNull
    static JsonMap createMetadata(@NonNull Locale locale) {
        return JsonMap.newBuilder()
                      .putOpt(RemoteDataPayload.METADATA_SDK_VERSION, UAirship.getVersion())
                      .putOpt(RemoteDataPayload.METADATA_COUNTRY, UAStringUtil.nullIfEmpty(locale.getCountry()))
                      .putOpt(RemoteDataPayload.METADATA_LANGUAGE, UAStringUtil.nullIfEmpty(locale.getLanguage()))
                      .build();
    }

    /**
     * Checks if the last metadata is current.
     *
     * @return {@code true} if the metadata is current, otherwise {@code false}.
     */
    public boolean isMetadataCurrent(@NonNull JsonMap jsonMap) {
        return jsonMap.equals(createMetadata(localeManager.getDefaultLocale()));
    }

    /**
     * Checks if the last metadata is current.
     *
     * @return {@code true} if the metadata is current, otherwise {@code false}.
     */
    public boolean isLastMetadataCurrent() {
        return isMetadataCurrent(getLastMetadata());
    }

    /**
     * Gets the metadata used to fetch the last payload.
     *
     * @return The last used metadata.
     */
    public JsonMap getLastMetadata() {
        return preferenceDataStore.getJsonValue(LAST_REFRESH_METADATA).optMap();
    }

}
