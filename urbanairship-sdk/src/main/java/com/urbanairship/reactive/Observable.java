/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

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
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Observable<T> {

    private Function<Observer<T>, Subscription> onSubscribe;

    /**
     * Factory method for creating Observables.
     *
     * @param func A function mapping observers to Subscriptions.
     * @param <T> The type of the value.
     * @return An Observable of the underlying type.
     */
    public static <T> Observable<T> create(Function<Observer<T>, Subscription> func) {
        Observable<T> observable = new Observable<>();
        observable.onSubscribe = func;
        return observable;
    }

    /**
     * Creates an Observable that sends a single value and then completes.
     *
     * @param value The value to send.
     * @param <T> The type of the value under observation.
     * @return An Observable of the underlying type.
     */
    public static <T> Observable<T> just(final T value) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
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
    public static <T> Observable<T> empty() {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
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
    public static <T> Observable<T> never() {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(Observer<T> observer) {
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
    public static <T> Observable<T> error(final Exception e) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
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
    public static <T> Observable<T> from(final Collection<T> collection) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
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
    public Subscription subscribe(Observer<T> observer) {
        return this.onSubscribe.apply(observer);
    }

    /**
     * Maps values to new Observables which are flattened into the result stream.
     *
     * @param func The map function
     * @param <R> The type under observation of the result observable
     * @return A mapped Observable.
     */
    public <R> Observable<R> flatMap(final Function<T, Observable<R>> func) {
        return bind(new Function<T, Observable<R>>() {
            @Override
            public Observable<R> apply(T value) {
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
    public <R> Observable<R> map(final Function<T, R> func) {
        return flatMap(new Function<T, Observable<R>>() {
            @Override
            public Observable<R> apply(T value) {
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
    public Observable<T> filter(final Predicate<T> pred) {
        return flatMap(new Function<T, Observable<T>>() {
            @Override
            public Observable<T> apply(T value) {
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
    public Observable<T> distinctUntilChanged() {
        final Holder<T> lastValue = new Holder<>();
        return bind(new Function<T, Observable<T>>() {
            @Override
            public Observable<T> apply(T value) {
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
    public Observable<T> defaultIfEmpty(final T defaultValue) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
                final AtomicBoolean empty = new AtomicBoolean(true);
                return subscribe(new Observer<T>() {
                    @Override
                    public void onNext(T value) {
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
                    public void onError(Exception e) {
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
    public Observable<T> observeOn(final Scheduler scheduler) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
                final SerialSubscription subscription = new SerialSubscription();

                subscription.setSubscription(subscribe(new Observer<T>() {
                    @Override
                    public void onNext(final T value) {
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
                    public void onError(final Exception e) {
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
    public Observable<T>subscribeOn(final Scheduler scheduler) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
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
    public static <T> Observable<T> merge(final Observable<T> lh, final Observable<T> rh) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
                final AtomicInteger completed = new AtomicInteger(0);
                final CompoundSubscription compoundSubscription = new CompoundSubscription();

                final Observer<T> innerObserver = new Observer<T>() {
                    @Override
                    public void onNext(T value) {
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
                    public void onError(Exception e) {
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
    public static <T> Observable<T> merge(Collection<Observable<T>> observables) {
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
    public static <T> Observable<T> concat(final Observable<T> lh, final Observable<T> rh) {
        final CompoundSubscription compoundSubscription = new CompoundSubscription();

        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
                compoundSubscription.add(lh.subscribe(new Observer<T>() {
                    @Override
                    public void onNext(T value) {
                        observer.onNext(value);
                    }

                    @Override
                    public void onCompleted() {
                        compoundSubscription.add(rh.subscribe(observer));
                    }

                    @Override
                    public void onError(Exception e) {
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
    public static <T> Observable<T> defer(final Supplier<Observable<T>> func) {
        return Observable.create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(Observer<T> observer) {
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
    public static <T, R> Observable<R> zip(final Observable<T> lh, final Observable<T> rh, final BiFunction<T, T, R> func) {
        return create(new Function<Observer<R>, Subscription>() {
            @Override
            public Subscription apply(final Observer<R> observer) {
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
                    public void onNext(T value) {
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
                    public void onError(Exception e) {
                        synchronized (observer) {
                            compoundSubscription.cancel();
                            observer.onError(e);
                        }
                    }
                }));

                compoundSubscription.add(rh.subscribe(new Subscriber<T>() {
                    @Override
                    public void onNext(T value) {
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
                    public void onError(Exception e) {
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
    private <R> Observable<R> bind(final Function<T, Observable<R>> binding) {
        final WeakReference<Observable<T>> weakThis = new WeakReference<>(this);
        final CompoundSubscription compoundSubscription = new CompoundSubscription();

        return Observable.create(new Function<Observer<R>, Subscription>() {
            @Override
            public Subscription apply(final Observer<R> observer) {
                final ObservableTracker<R> tracker = new ObservableTracker<>(observer, compoundSubscription);

                Observable<T> originalObservable = weakThis.get();
                // This should not happen
                if (originalObservable == null) {
                    observer.onCompleted();
                    return Subscription.empty();
                }

                final SerialSubscription thisSubscription = new SerialSubscription();
                compoundSubscription.add(thisSubscription);

                thisSubscription.setSubscription(originalObservable.subscribe(new Subscriber<T>(){
                    @Override
                    public void onNext(T value) {
                        final Observable<R> bound = binding.apply(value);
                        if (bound != null & !compoundSubscription.isCancelled()) {
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
                    public void onError(Exception e) {
                        compoundSubscription.cancel();
                        observer.onError(e);
                    }
                }));

                return compoundSubscription;
            }
        });
    }

    /**
     *  Generic value holder class.
     * @param <T> The type contained.
     */
    private static class Holder<T> {
        private T value;

        Holder() {};

        Holder(T initial) {
            this.value = initial;
        }

        T getValue() { return value; };
        void setValue(T v) { value = v; };
    }

    /**
     * Inner class facilitating bind operation.
     *
     * @param <T> The underlying type of the Observables
     */
    private static class ObservableTracker<T> {
        private Observer<T> observer;
        private CompoundSubscription compoundSubscription;
        private AtomicInteger observableCount = new AtomicInteger(1);

        ObservableTracker(Observer<T> observer, CompoundSubscription compoundSubscription) {
            this.observer = observer;
            this.compoundSubscription = compoundSubscription;
        }

        void addObservable(final Observable<T> observable) {
            observableCount.getAndIncrement();

            final SerialSubscription thisSubscription = new SerialSubscription();
            thisSubscription.setSubscription(observable.subscribe(new Observer<T>() {
                @Override
                public void onNext(T value) {
                    observer.onNext(value);
                }

                @Override
                public void onCompleted() {
                    completeObservable(thisSubscription);
                }

                @Override
                public void onError(Exception e) {
                    compoundSubscription.cancel();
                    observer.onError(e);
                }
            }));
        }

        void completeObservable(Subscription subscription) {
            if (observableCount.decrementAndGet() == 0) {
                observer.onCompleted();
                compoundSubscription.cancel();
            } else {
                compoundSubscription.remove(subscription);
            }
        }
    }
}
