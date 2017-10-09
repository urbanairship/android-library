/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import com.urbanairship.Predicate;

import java.lang.ref.WeakReference;
import java.util.Collection;
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
     * Transforms an Observable stream to deliver its callbacks on the supplied scheduler.
     *
     * @param scheduler The scheduler.
     * @return A transformed Observable whose callbacks are delivered on the supplied scheduler.
     */
    public Observable<T> observeOn(final Scheduler scheduler) {
        return create(new Function<Observer<T>, Subscription>() {
            @Override
            public Subscription apply(final Observer<T> observer) {
                final CompoundSubscription subscription = new CompoundSubscription();

                subscription.add(subscribe(new Observer<T>() {
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
     * Bind operator for observables
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
                final ObservableTracker holder = new ObservableTracker(observer, compoundSubscription);

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
                        if (bound != null) {
                            holder.addObservable(bound);
                        } else {
                            // Early termination
                            thisSubscription.cancel();
                            holder.completeObservable(thisSubscription);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        holder.completeObservable(thisSubscription);
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
     * Inner class facilitating bind operation.
     *
     * @param <T> The underlying type of the Observables
     */
    private static class ObservableTracker<T> {
        private Observer observer;
        private CompoundSubscription compoundSubscription;
        private AtomicInteger observableCount = new AtomicInteger(0);

        ObservableTracker(Observer observer, CompoundSubscription compoundSubscription) {
            this.observer = observer;
            this.compoundSubscription = compoundSubscription;
        }

        public void addObservable(final Observable observable) {
            observableCount.getAndIncrement();

            final SerialSubscription thisSubscription = new SerialSubscription();
            thisSubscription.setSubscription(observable.subscribe(new Observer() {
                @Override
                public void onNext(Object value) {
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

        public void completeObservable(Subscription subscription) {
            if (observableCount.decrementAndGet() == 0) {
                observer.onCompleted();
                compoundSubscription.cancel();
            } else {
                compoundSubscription.remove(subscription);
            }
        }
    }
}
