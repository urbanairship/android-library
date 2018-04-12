/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Observer;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.reactive.Supplier;

/**
 * Factory methods for creating compound trigger observables
 *
 * @hide
 */
class TriggerObservables {

    /**
     * Creates a state observable that sends onNext if the app is currently foregrounded,
     * and completes.
     *
     * @param monitor An instance of ActivityMonitor.
     * @return An Observable of JsonSerializable.
     */
    public static Observable<JsonSerializable> foregrounded(final ActivityMonitor monitor) {
        return Observable.create(new Function<Observer<JsonSerializable>, Subscription>() {
            @Override
            public Subscription apply(Observer<JsonSerializable> observer) {
                if (monitor.isAppForegrounded()) {
                    observer.onNext(JsonValue.NULL);
                }
                observer.onCompleted();
                return Subscription.empty();
            }
        }).subscribeOn(Schedulers.main());
    }

    /**
     * Creates an event observable that sends onNext when a new session begins.
     *
     * @param monitor An instance of ActivityMonitor.
     * @return An Observable of JsonSerializable.
     */
    public static Observable<JsonSerializable> newSession(final ActivityMonitor monitor) {
        return Observable.create(new Function<Observer<JsonSerializable>, Subscription>() {
            @Override
            public Subscription apply(final Observer<JsonSerializable> observer) {
                final ActivityMonitor.SimpleListener listener = new ActivityMonitor.SimpleListener() {
                    @Override
                    public void onForeground(long time) {
                        observer.onNext(JsonValue.NULL);
                    }
                };

                monitor.addListener(listener);

                return Subscription.create(new Runnable() {
                    @Override
                    public void run() {
                        monitor.removeListener(listener);
                    }
                });
            }
        }).subscribeOn(Schedulers.main());
    }

    /**
     * Creates a state observable that sends onNext if the app version is currently updated, and then completes.
     *
     * The JSON payload contains a key value pair of the device platform (android or amazon) and
     * the current app version, e.g. <code>{"android": {"version": 123}}</code>.
     *
     * @return An Observable of JsonSerializable.
     */
    public static Observable<JsonSerializable> appVersionUpdated() {
        return Observable.defer(new Supplier<Observable<JsonSerializable>>() {
            @Override
            public Observable<JsonSerializable> apply() {
                if (UAirship.shared().getApplicationMetrics().getAppVersionUpdated()) {
                    return Observable.just(AutomationUtils.createVersionObject());
                } else {
                    return Observable.empty();
                }
            }
        });
    }
}
