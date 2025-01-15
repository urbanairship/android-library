/* Copyright Airship and Contributors */

package com.urbanairship.job;

import com.urbanairship.util.Clock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Rate limit tracker.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RateLimiter {

    private final Clock clock;
    private final Map<String, List<Long>> hits = new HashMap<>();
    private final Map<String, Rule> rules = new HashMap<>();
    private final Object lock = new Object();

    public RateLimiter() {
        this(Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Tracks a rate limit.
     *
     * @param limitId The limit Id.
     */
    public void track(@NonNull String limitId) {
        synchronized (lock) {
            List<Long> hits = this.hits.get(limitId);
            Rule rule = this.rules.get(limitId);
            long currentTime = clock.currentTimeMillis();
            if (hits != null && rule != null) {
                hits.add(currentTime);
                filter(hits, rule, currentTime);
            }
        }
    }

    /**
     * Gets the rate limit status.
     *
     * @param limitId The limit Id.
     * @return The status if a limit exists, otherwise {@code null}.
     */
    @Nullable
    public Status status(@NonNull String limitId) {
        synchronized (lock) {
            List<Long> hits = this.hits.get(limitId);
            Rule rule = this.rules.get(limitId);
            long currentTime = clock.currentTimeMillis();

            if (hits == null || rule == null) {
                return null;
            }

            filter(hits, rule, currentTime);

            if (hits.size() >= rule.rate) {
                long nextExpired = rule.durationMs - (currentTime - hits.get(hits.size() - rule.rate));
                return new Status(LimitStatus.OVER, nextExpired);
            } else {
                return new Status(LimitStatus.UNDER, 0);
            }
        }
    }

    /**
     * Sets the limit.
     *
     * @param limitId Limit Id.
     * @param rate The number of events for the duration.
     * @param duration The duration.
     * @param durationUnit The duration unit.
     */
    public void setLimit(@NonNull String limitId, @IntRange(from = 1) int rate, long duration, @NonNull TimeUnit durationUnit) {
        synchronized (lock) {
            this.rules.put(limitId, new Rule(rate, durationUnit.toMillis(duration)));
            this.hits.put(limitId, new ArrayList<>());
        }
    }

    private void filter(@NonNull List<Long> hits, @NonNull Rule rule, long currentTimeMs) {
        for (long hit : new ArrayList<>(hits)) {
            if (currentTimeMs >= (hit + rule.durationMs)) {
                hits.remove(hit);
            }
        }
    }

    /**
     * Limit status.
     */
    public enum LimitStatus {
        /**
         * Over the limit.
         */
        OVER,

        /**
         * Under the limit.
         */
        UNDER
    }

    /**
     * Rate limit status.
     */
    public static final class Status {

        private final LimitStatus limitStatus;
        private final long nextAvailableMs;

        @VisibleForTesting
        public Status(@NonNull LimitStatus limitStatus, long nextAvailableMs) {
            this.limitStatus = limitStatus;
            this.nextAvailableMs = nextAvailableMs;
        }

        /**
         * The status.
         *
         * @return The status.
         */
        public LimitStatus getLimitStatus() {
            return limitStatus;
        }

        /**
         * When the limit will be under.
         *
         * @param timeUnit The time unit.
         * @return The time when the limit will be back under.
         */
        public long getNextAvailable(@NonNull TimeUnit timeUnit) {
            return timeUnit.convert(nextAvailableMs, TimeUnit.MILLISECONDS);
        }
    }

    private static final class Rule {

        final long durationMs;
        final int rate;

        Rule(int rate, long durationMs) {
            this.rate = rate;
            this.durationMs = durationMs;
        }

    }

}
