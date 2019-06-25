/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Predicate;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observable implementation for creating push-based sequences.
 *
 * @param <T> The type of the value under observation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Observable<T> {

    @Nullable
    protected final Function<Observer<T>, Subscription> onSubscribe;

    protected Observable() {
        this(null);
    }

    protected Observable(@Nullable Function<Observer<T>, Subscription> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    /**
     * Factory method for creating Observables.
     *
     * @param func A function mapping observers to Subscriptions.
     * @param <T> The type of the value.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> create(@NonNull Function<Observer<T>, Subscription> func) {
        return new Observable<>(func);
    }

    /**
     * Creates an Observable that sends a single value and then completes.
     *
     * @param value The value to send.
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> just(@NonNull final T value) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                observer.onNext(value);
                observer.onCompleted();
                return Subscription.empty();
            }
        });
    }

    /**
     * Creates an Observable that immediately completes.
     *
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> empty() {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                observer.onCompleted();
                return Subscription.empty();
            }
        });
    }

    /**
     * Creates an Observable that never completes.
     *
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> never() {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull Observer<T> observer) {
                return Subscription.empty();
            }
        });
    }

    /**
     * Creates an Observable that sends an error.
     *
     * @param e The error.
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> error(@NonNull final Exception e) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                observer.onError(e);
                return Subscription.empty();
            }
        });
    }

    /**
     * Converts a collection into an Observable over the values in that collection.
     *
     * @param collection The collection.
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    @NonNull
    public static <T> Observable<T> from(@NonNull final Collection<T> collection) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                for (final T value : collection) {
                    observer.onNext(value);
                }

                observer.onCompleted();

                return Subscription.empty();
            }
        });
    }

    /**
     * Subscribes the provided Observer.
     *
     * @param observer The Observer.
     * @return A Cancellable that unsubscribes the Observer when canceled.
     */
    @NonNull
    public Subscription subscribe(@NonNull Observer<T> observer) {
        if (onSubscribe != null) {
            return this.onSubscribe.apply(observer);
        } else {
            return Subscription.empty();
        }
    }

    /**
     * Maps values to new Observables which are flattened into the result stream.
     *
     * @param func The map function
     * @param <R> The type under observation of the result observable
     * @return A mapped Observable.
     */
    @NonNull
    public <R> Observable<R> flatMap(@NonNull final Function<T, Observable<R>> func) {
        return bind(new Function<T, Observable<R>>() {
            @NonNull
            @Override
            public Observable<R> apply(@NonNull T value) {
                return func.apply(value);
            }
        });
    }

    /**
     * Maps values in an Observable stream to new values
     *
     * @param func The map function
     * @param <R> The type under observation of the result Observable.
     * @return A mapped Observable.
     */
    @NonNull
    public <R> Observable<R> map(@NonNull final Function<T, R> func) {
        return flatMap(new Function<T, Observable<R>>() {
            @NonNull
            @Override
            public Observable<R> apply(@NonNull T value) {
                return just(func.apply(value));
            }
        });
    }

    /**
     * Filters values out of an Observable stream that do not pass the provided predicate.
     *
     * @param pred The predicate.
     * @return A filtered Observable.
     */
    @NonNull
    public Observable<T> filter(@NonNull final Predicate<T> pred) {
        return flatMap(new Function<T, Observable<T>>() {
            @NonNull
            @Override
            public Observable<T> apply(@NonNull T value) {
                if (pred.apply(value)) {
                    return just(value);
                } else {
                    return empty();
                }
            }
        });
    }

    /**
     * Transforms an Observable stream to only deliver callbacks if a new value is distinct from the previous one.
     *
     * @return A transformed Observable.
     */
    @NonNull
    public Observable<T> distinctUntilChanged() {
        final Holder<T> lastValue = new Holder<>();
        return bind(new Function<T, Observable<T>>() {
            @NonNull
            @Override
            public Observable<T> apply(@NonNull T value) {
                if (lastValue.getValue() != null && value.equals(lastValue.getValue())) {
                    return empty();
                }

                lastValue.setValue(value);

                return just(value);
            }
        });
    }

    /**
     * Transforms an Observable stream to deliver a default value if the original is empty.
     *
     * @param defaultValue The default value.
     * @return A transformed Observable.
     */
    @NonNull
    public Observable<T> defaultIfEmpty(@NonNull final T defaultValue) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                final AtomicBoolean empty = new AtomicBoolean(true);
                return subscribe(new Observer<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        observer.onNext(value);
                        empty.set(false);
                    }

                    @Override
                    public void onCompleted() {
                        if (empty.get()) {
                            observer.onNext(defaultValue);
                        }
                        observer.onCompleted();
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        observer.onCompleted();
                    }
                });
            }
        });
    }

    /**
     * Transforms an Observable stream to deliver its callbacks on the supplied scheduler.
     *
     * @param scheduler The scheduler.
     * @return A transformed Observable whose callbacks are delivered on the supplied scheduler.
     */
    @NonNull
    public Observable<T> observeOn(@NonNull final Scheduler scheduler) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                final SerialSubscription subscription = new SerialSubscription();

                subscription.setSubscription(subscribe(new Observer<T>() {
                    @Override
                    public void onNext(@NonNull final T value) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (!subscription.isCancelled()) {
                                    observer.onNext(value);
                                }
                            }
                        });
                    }

                    @Override
                    public void onCompleted() {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (!subscription.isCancelled()) {
                                    observer.onCompleted();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull final Exception e) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (!subscription.isCancelled()) {
                                    observer.onError(e);
                                }
                            }
                        });
                    }
                }));

                return subscription;
            }
        });
    }

    /**
     * Transforms an Observable to perform its subscription work on the supplied scheduler.
     *
     * @param scheduler The scheduler.
     * @return A transformed Observable that performs its subscription work on the supplied scheduler.
     */
    @NonNull
    public Observable<T> subscribeOn(@NonNull final Scheduler scheduler) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                final CompoundSubscription compoundSubscription = new CompoundSubscription();

                compoundSubscription.add(scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        compoundSubscription.add(subscribe(observer));
                    }
                }));

                return compoundSubscription;
            }
        });
    }

    /**
     * Merges the values of two Observables in the order they are received.
     *
     * @param lh The left Observable
     * @param rh The right Observable
     * @param <T> The type under observation
     * @return A merged Observable.
     */
    @NonNull
    public static <T> Observable<T> merge(@NonNull final Observable<T> lh, @NonNull final Observable<T> rh) {
        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                final AtomicInteger completed = new AtomicInteger(0);
                final CompoundSubscription compoundSubscription = new CompoundSubscription();

                final Observer<T> innerObserver = new Observer<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        synchronized (observer) {
                            observer.onNext(value);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        synchronized (observer) {
                            if (completed.incrementAndGet() == 2) {
                                observer.onCompleted();
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        synchronized (observer) {
                            compoundSubscription.cancel();
                            observer.onError(e);
                        }
                    }
                };

                compoundSubscription.add(lh.subscribe(innerObserver));
                compoundSubscription.add(rh.subscribe(innerObserver));

                return compoundSubscription;
            }
        });
    }

    /**
     * Merges the values of a collection of Observables in the order they are received.
     *
     * @param observables The observables
     * @param <T> The type under observation.
     * @return A merged Observable
     */
    @NonNull
    public static <T> Observable<T> merge(@NonNull Collection<Observable<T>> observables) {
        Observable<T> next = empty();
        for (Observable<T> observable : observables) {
            next = merge(next, observable);
        }
        return next;
    }

    /**
     * Concatenates values from two Observables, by subscribing to left-hand Observable first and
     * subscribing to the right-hand Observable once the first has completed.
     *
     * @param lh The left Observable.
     * @param rh The right Observable.
     * @param <T> The type under observation.
     * @return The concatenated observable.
     */
    @NonNull
    public static <T> Observable<T> concat(@NonNull final Observable<T> lh, @NonNull final Observable<T> rh) {
        final CompoundSubscription compoundSubscription = new CompoundSubscription();

        return create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<T> observer) {
                compoundSubscription.add(lh.subscribe(new Observer<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        observer.onNext(value);
                    }

                    @Override
                    public void onCompleted() {
                        compoundSubscription.add(rh.subscribe(observer));
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        observer.onError(e);
                    }
                }));

                return Subscription.create(new Runnable() {
                    @Override
                    public void run() {
                        compoundSubscription.cancel();
                    }
                });
            }
        });
    }

    /**
     * Creates an Observable that is lazily created at subscription time, instead of creation time,
     * by the provided function.
     *
     * @param func A supplier function that produces an Observable.
     * @param <T> The type under observation.
     * @return A deferred Observable.
     */
    @NonNull
    public static <T> Observable<T> defer(@NonNull final Supplier<Observable<T>> func) {
        return Observable.create(new Function<Observer<T>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull Observer<T> observer) {
                return func.apply().subscribe(observer);
            }
        });
    }

    /**
     * Combines two Observable streams by waiting for each to emit a new value and emitting a composite value by applying
     * the provided function.
     *
     * @param lh The left Observable.
     * @param rh The right Observable.
     * @param func A function for producing composite values.
     * @param <T> The underlying type of the source Observables.
     * @param <R> The composite type.
     * @return A transformed Observable.
     */
    @NonNull
    public static <T, R> Observable<R> zip(@NonNull final Observable<T> lh, @NonNull final Observable<T> rh, @NonNull final BiFunction<T, T, R> func) {
        return create(new Function<Observer<R>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<R> observer) {
                final CompoundSubscription compoundSubscription = new CompoundSubscription();

                final ArrayList<T> lhValues = new ArrayList<>();
                final ArrayList<T> rhValues = new ArrayList<>();

                final Holder<Boolean> lhCompleted = new Holder<>(false);
                final Holder<Boolean> rhCompleted = new Holder<>(false);
                final Holder<Boolean> completed = new Holder<>(false);

                final Runnable completeIfNeeded = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (observer) {
                            if (completed.getValue()) {
                                return;
                            }
                            if ((lhCompleted.getValue() && lhValues.size() == 0) && rhCompleted.getValue() && rhValues.size() == 0) {
                                completed.setValue(true);
                                compoundSubscription.cancel();
                                observer.onCompleted();
                            }
                        }
                    }
                };

                final Runnable emitNextIfNeeded = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (observer) {
                            if (lhValues.size() > 0 && rhValues.size() > 0) {
                                R zipped = func.apply(lhValues.get(0), rhValues.get(0));

                                lhValues.remove(0);
                                rhValues.remove(0);

                                observer.onNext(zipped);

                                completeIfNeeded.run();
                            }
                        }
                    }
                };

                compoundSubscription.add(lh.subscribe(new Subscriber<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        synchronized (observer) {
                            lhValues.add(value);
                            emitNextIfNeeded.run();
                        }
                    }

                    @Override
                    public void onCompleted() {
                        synchronized (observer) {
                            lhCompleted.setValue(true);
                            completeIfNeeded.run();
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        synchronized (observer) {
                            compoundSubscription.cancel();
                            observer.onError(e);
                        }
                    }
                }));

                compoundSubscription.add(rh.subscribe(new Subscriber<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        synchronized (observer) {
                            rhValues.add(value);
                            emitNextIfNeeded.run();
                        }
                    }

                    @Override
                    public void onCompleted() {
                        synchronized (observer) {
                            rhCompleted.setValue(true);
                            completeIfNeeded.run();
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        synchronized (observer) {
                            compoundSubscription.cancel();
                            observer.onError(e);
                        }
                    }
                }));

                return compoundSubscription;
            }
        });
    }

    /**
     * Bind operator for Observables
     *
     * @param binding A function mapping the underlying source type to an observable of the return type
     * @param <R> The return type
     * @return An observable of the return type
     */
    @NonNull
    private <R> Observable<R> bind(@NonNull final Function<T, Observable<R>> binding) {
        final WeakReference<Observable<T>> weakThis = new WeakReference<>(this);
        final CompoundSubscription compoundSubscription = new CompoundSubscription();

        return Observable.create(new Function<Observer<R>, Subscription>() {
            @NonNull
            @Override
            public Subscription apply(@NonNull final Observer<R> observer) {
                final ObservableTracker<R> tracker = new ObservableTracker<>(observer, compoundSubscription);

                Observable<T> originalObservable = weakThis.get();
                // This should not happen
                if (originalObservable == null) {
                    observer.onCompleted();
                    return Subscription.empty();
                }

                final SerialSubscription thisSubscription = new SerialSubscription();
                compoundSubscription.add(thisSubscription);

                thisSubscription.setSubscription(originalObservable.subscribe(new Subscriber<T>() {
                    @Override
                    public void onNext(@NonNull T value) {
                        if (!compoundSubscription.isCancelled()) {
                            Observable<R> bound = binding.apply(value);
                            tracker.addObservable(bound);
                        } else {
                            // Early termination
                            thisSubscription.cancel();
                            tracker.completeObservable(thisSubscription);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        tracker.completeObservable(thisSubscription);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        compoundSubscription.cancel();
                        observer.onError(e);
                    }
                }));

                return compoundSubscription;
            }
        });
    }

    /**
     * Generic value holder class.
     *
     * @param <T> The type contained.
     */
    private static class Holder<T> {

        private T value;

        Holder() {
        }

        Holder(T initial) {
            this.value = initial;
        }

        T getValue() {
            return value;
        }

        void setValue(T v) {
            value = v;
        }

    }

    /**
     * Inner class facilitating bind operation.
     *
     * @param <T> The underlying type of the Observables
     */
    private static class ObservableTracker<T> {

        private final Observer<T> observer;
        private final CompoundSubscription compoundSubscription;
        private final AtomicInteger observableCount = new AtomicInteger(1);

        ObservableTracker(Observer<T> observer, CompoundSubscription compoundSubscription) {
            this.observer = observer;
            this.compoundSubscription = compoundSubscription;
        }

        void addObservable(@NonNull final Observable<T> observable) {
            observableCount.getAndIncrement();

            final SerialSubscription thisSubscription = new SerialSubscription();
            thisSubscription.setSubscription(observable.subscribe(new Observer<T>() {
                @Override
                public void onNext(@NonNull T value) {
                    observer.onNext(value);
                }

                @Override
                public void onCompleted() {
                    completeObservable(thisSubscription);
                }

                @Override
                public void onError(@NonNull Exception e) {
                    compoundSubscription.cancel();
                    observer.onError(e);
                }
            }));
        }

        void completeObservable(@NonNull Subscription subscription) {
            if (observableCount.decrementAndGet() == 0) {
                observer.onCompleted();
                compoundSubscription.cancel();
            } else {
                compoundSubscription.remove(subscription);
            }
        }

    }

}
