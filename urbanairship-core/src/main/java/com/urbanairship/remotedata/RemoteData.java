/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.PushProviders;
import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.locale.LocaleChangedListener;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Supplier;
import com.urbanairship.util.AirshipHandlerThread;
import com.urbanairship.util.Clock;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

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
    /**
     * Key for the URL that was used to fetch the remote-data in the metadata.
     */
    private static final String URL_METADATA_KEY = "url";

    /**
     * Action to refresh remote data.
     *
     * @hide
     */
    @VisibleForTesting
    static final String ACTION_REFRESH = "ACTION_REFRESH";

    private final JobDispatcher jobDispatcher;
    private final PreferenceDataStore preferenceDataStore;
    private Handler backgroundHandler;
    private final ActivityMonitor activityMonitor;
    private final LocaleManager localeManager;
    private final PushManager pushManager;
    private final Clock clock;
    private final RemoteDataApiClient apiClient;
    private final PrivacyManager privacyManager;

    @VisibleForTesting
    final Subject<Set<RemoteDataPayload>> payloadUpdates;

    @VisibleForTesting
    final HandlerThread backgroundThread;

    @VisibleForTesting
    final RemoteDataStore dataStore;

    private final ApplicationListener applicationListener = new SimpleApplicationListener() {
        @Override
        public void onForeground(long time) {
            if (shouldRefresh()) {
                refresh();
            }
        }
    };

    private final LocaleChangedListener localeChangedListener = new LocaleChangedListener() {
        @Override
        public void onLocaleChanged(@NonNull Locale locale) {
            if (shouldRefresh()) {
                refresh();
            }
        }
    };

    private final PushListener pushListener = new PushListener() {
        @WorkerThread
        @Override
        public void onPushReceived(@NonNull PushMessage message, boolean notificationPosted) {
            if (message.isRemoteDataUpdate()) {
                refresh();
            }
        }
    };

    private final PrivacyManager.Listener privacyListener = new PrivacyManager.Listener() {
        @Override
        public void onEnabledFeaturesChanged() {
            if (shouldRefresh()) {
                refresh();
            }
        }
    };

    /**
     * RemoteData constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The preference data store
     * @param configOptions The config options.
     * @param privacyManager Privacy manager.
     * @param pushManager The push manager.
     * @param localeManager The locale manager.
     * @param pushProviders The push providers.
     */
    public RemoteData(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                      @NonNull AirshipRuntimeConfig configOptions, @NonNull PrivacyManager privacyManager,
                      @NonNull PushManager pushManager, @NonNull LocaleManager localeManager,
                      @NonNull com.urbanairship.base.Supplier<PushProviders> pushProviders) {
        this(context, preferenceDataStore, configOptions, privacyManager, GlobalActivityMonitor.shared(context),
                JobDispatcher.shared(context), localeManager, pushManager, Clock.DEFAULT_CLOCK,
                new RemoteDataApiClient(configOptions, pushProviders));
    }

    @VisibleForTesting
    RemoteData(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
               @NonNull AirshipRuntimeConfig configOptions, @NonNull PrivacyManager privacyManager,
               @NonNull ActivityMonitor activityMonitor, @NonNull JobDispatcher dispatcher,
               @NonNull LocaleManager localeManager, @NonNull PushManager pushManager,
               @NonNull Clock clock, @NonNull RemoteDataApiClient apiClient) {
        super(context, preferenceDataStore);
        this.jobDispatcher = dispatcher;
        this.dataStore = new RemoteDataStore(context, configOptions.getConfigOptions().appKey, DATABASE_NAME);
        this.preferenceDataStore = preferenceDataStore;
        this.privacyManager = privacyManager;
        this.backgroundThread = new AirshipHandlerThread("remote data store");
        this.payloadUpdates = Subject.create();
        this.activityMonitor = activityMonitor;
        this.localeManager = localeManager;
        this.pushManager = pushManager;
        this.clock = clock;
        this.apiClient = apiClient;
    }

    @Override
    protected void init() {
        super.init();
        backgroundThread.start();
        backgroundHandler = new Handler(this.backgroundThread.getLooper());

        activityMonitor.addApplicationListener(applicationListener);
        pushManager.addInternalPushListener(pushListener);
        localeManager.addListener(localeChangedListener);
        privacyManager.addListener(privacyListener);

        if (shouldRefresh()) {
            refresh();
        }
    }

    @Override
    protected void tearDown() {
        pushManager.removePushListener(pushListener);
        activityMonitor.removeApplicationListener(applicationListener);
        localeManager.removeListener(localeChangedListener);
        privacyManager.removeListener(privacyListener);
        backgroundThread.quit();
    }

    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (!privacyManager.isAnyFeatureEnabled()) {
            return JobInfo.JOB_FINISHED;
        }

        if (ACTION_REFRESH.equals(jobInfo.getAction())) {
            return onRefresh();
        }

        return JobInfo.JOB_FINISHED;
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
                                 .setAction(ACTION_REFRESH)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(RemoteData.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onUrlConfigUpdated() {
        // Update remote data when notified of new URL config.
        refresh();
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

    private boolean shouldRefresh() {
        if (!privacyManager.isAnyFeatureEnabled()) {
            return false;
        }

        if (!activityMonitor.isAppForegrounded()) {
            return false;
        }

        long timeSinceLastRefresh = clock.currentTimeMillis() - preferenceDataStore.getLong(LAST_REFRESH_TIME_KEY, -1);
        if (getForegroundRefreshInterval() <= timeSinceLastRefresh) {
            return true;
        }

        long appVersion = preferenceDataStore.getLong(LAST_REFRESH_APP_VERSION_KEY, 0);
        PackageInfo packageInfo = UAirship.getPackageInfo();
        if (packageInfo != null && PackageInfoCompat.getLongVersionCode(packageInfo) != appVersion) {
            return true;
        }

        if (!isLastMetadataCurrent()) {
            return true;
        }

        return false;
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
     * Checks if the last metadata is current.
     *
     * @return {@code true} if the metadata is current, otherwise {@code false}.
     */
    public boolean isMetadataCurrent(@NonNull JsonMap jsonMap) {
        Uri uri = apiClient.getRemoteDataUrl(localeManager.getLocale());
        return jsonMap.equals(createMetadata(uri));
    }

    /**
     * Checks if the last metadata is current.
     *
     * @return {@code true} if the metadata is current, otherwise {@code false}.
     */
    private boolean isLastMetadataCurrent() {
        return isMetadataCurrent(preferenceDataStore.getJsonValue(LAST_REFRESH_METADATA).optMap());
    }

    /**
     * Refreshes the remote data, performing a callback into RemoteData if there
     * is anything new to process.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onRefresh() {
        String lastModified = isLastMetadataCurrent() ? preferenceDataStore.getString(LAST_MODIFIED_KEY, null) : null;
        Locale locale = localeManager.getLocale();

        Response<RemoteDataApiClient.Result> response;
        try {
            response = apiClient.fetchRemoteDataPayloads(lastModified, locale, new RemoteDataApiClient.PayloadParser() {
                @Override
                public Set<RemoteDataPayload> parse(Uri url, JsonList payloads) {
                    return RemoteDataPayload.parsePayloads(payloads, createMetadata(url));
                }
            });
        } catch (RequestException e) {
            Logger.error(e, "RemoteDataJobHandler - Failed to refresh data");
            return JobInfo.JOB_FINISHED;
        }

        Logger.debug("Received remote data response: %s", response);

        if (response.getStatus() == 304) {
            onRefreshFinished();
            return JobInfo.JOB_FINISHED;
        }

        if (response.isSuccessful()) {
            JsonMap metadata = createMetadata(response.getResult().url);
            Set<RemoteDataPayload> remoteDataPayloads = response.getResult().payloads;
            if (saveNewPayloads(remoteDataPayloads)) {
                preferenceDataStore.put(LAST_REFRESH_METADATA, metadata);
                preferenceDataStore.put(LAST_MODIFIED_KEY, response.getResponseHeader("Last-Modified"));
                notifyPayloadUpdates(remoteDataPayloads);
                onRefreshFinished();
                return JobInfo.JOB_FINISHED;
            }
            return JobInfo.JOB_RETRY;
        }

        // Error
        return response.isServerError() ? JobInfo.JOB_RETRY : JobInfo.JOB_FINISHED;
    }

    private void onRefreshFinished() {
        PackageInfo packageInfo = UAirship.getPackageInfo();
        if (packageInfo != null) {
            preferenceDataStore.put(LAST_REFRESH_APP_VERSION_KEY, PackageInfoCompat.getLongVersionCode(packageInfo));
        }
        preferenceDataStore.put(LAST_REFRESH_TIME_KEY, clock.currentTimeMillis());
    }

    private boolean saveNewPayloads(@NonNull final Set<RemoteDataPayload> payloads) {
        return dataStore.deletePayloads() && dataStore.savePayloads(payloads);
    }

    private void notifyPayloadUpdates(@NonNull final Set<RemoteDataPayload> payloads) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                payloadUpdates.onNext(payloads);
            }
        });
    }

    @NonNull
    private JsonMap createMetadata(@Nullable Uri uri) {
        return JsonMap.newBuilder()
                      .putOpt(URL_METADATA_KEY, uri == null ? null : uri.toString())
                      .build();
    }

}
