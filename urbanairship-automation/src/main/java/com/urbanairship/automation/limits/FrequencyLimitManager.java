/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.automation.limits.storage.ConstraintEntity;
import com.urbanairship.automation.limits.storage.FrequencyLimitDao;
import com.urbanairship.automation.limits.storage.FrequencyLimitDatabase;
import com.urbanairship.automation.limits.storage.OccurrenceEntity;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.util.Clock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Frequency limit manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FrequencyLimitManager {

    /*
     * A frequency checker will have a strong reference to the list of constraints entities. Once
     * the checker is cleaned up this should remove the values from the map.
     */
    private final Map<ConstraintEntity, List<OccurrenceEntity>> occurrencesMap = new WeakHashMap<>();

    /*
     * List of pending occurrences to write to the database.
     */
    private final List<OccurrenceEntity> pendingOccurrences = new ArrayList<>();

    private final Object lock = new Object();
    private final FrequencyLimitDao dao;
    private final Clock clock;
    private final Executor executor;

    public FrequencyLimitManager(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        this(FrequencyLimitDatabase.createDatabase(context, config).getDao(), Clock.DEFAULT_CLOCK, AirshipExecutors.newSerialExecutor());
    }

    @VisibleForTesting
    FrequencyLimitManager(@NonNull FrequencyLimitDao dao, @NonNull Clock clock, @NonNull Executor executor) {
        this.dao = dao;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * Gets a frequency checker for the current constraints.
     *
     * The checker will keep a snapshot of the constraint definition at the time of checker creation.
     * Any updates to the constraints will be ignored until a new checker is created.
     *
     * @param constraintIds The collection of constraint Ids.
     * @return A future for the checker.
     */
    @NonNull
    public Future<FrequencyChecker> getFrequencyChecker(@Nullable final Collection<String> constraintIds) {
        final PendingResult<FrequencyChecker> pendingResult = new PendingResult<>();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Collection<ConstraintEntity> constraints = fetchConstraints(constraintIds);
                    FrequencyChecker checker = new FrequencyChecker() {
                        @Override
                        public boolean isOverLimit() {
                            return FrequencyLimitManager.this.isOverLimit(constraints);
                        }

                        @Override
                        public boolean checkAndIncrement() {
                            return FrequencyLimitManager.this.checkAndIncrement(constraints);
                        }
                    };
                    pendingResult.setResult(checker);
                } catch (Exception e) {
                    Logger.error("Failed to fetch constraints.");
                    pendingResult.setResult(null);
                }
            }
        });

        return pendingResult;
    }

    /**
     * Called to update constraints.
     *
     * @param constraints The constraints.
     */
    public Future<Boolean> updateConstraints(@NonNull final Collection<FrequencyConstraint> constraints) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Collection<ConstraintEntity> constraintEntities = dao.getConstraints();

                    Map<String, ConstraintEntity> constraintEntityMap = new HashMap<>();
                    for (ConstraintEntity entity : constraintEntities) {
                        constraintEntityMap.put(entity.constraintId, entity);
                    }

                    for (FrequencyConstraint constraint : constraints) {
                        ConstraintEntity entity = new ConstraintEntity();
                        entity.constraintId = constraint.getId();
                        entity.count = constraint.getCount();
                        entity.range = constraint.getRange();

                        ConstraintEntity existing = constraintEntityMap.remove(constraint.getId());
                        if (existing != null) {
                            if (existing.range != entity.range) {
                                dao.delete(existing);
                                dao.insert(entity);
                            } else {
                                dao.update(entity);
                            }
                        } else {
                            dao.insert(entity);
                        }
                    }

                    dao.delete(constraintEntityMap.keySet());
                    pendingResult.setResult(true);
                } catch (Exception e) {
                    Logger.error(e, "Failed to update constraints");
                    pendingResult.setResult(false);
                }
            }
        });

        return pendingResult;
    }

    private boolean checkAndIncrement(@NonNull Collection<ConstraintEntity> constraints) {
        if (constraints.isEmpty()) {
            return true;
        }

        synchronized (lock) {
            if (isOverLimit(constraints)) {
                return false;
            }
            recordOccurrence(getConstraintIds(constraints));
            return true;
        }
    }

    private boolean isOverLimit(@NonNull Collection<ConstraintEntity> constraints) {
        if (constraints.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            for (ConstraintEntity constraint : constraints) {
                if (isConstraintOverLimit(constraint)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void recordOccurrence(@NonNull Set<String> constraintIds) {
        if (constraintIds.isEmpty()) {
            return;
        }

        long timeMillis = clock.currentTimeMillis();

        for (String id : constraintIds) {
            OccurrenceEntity occurrence = new OccurrenceEntity();
            occurrence.parentConstraintId = id;
            occurrence.timeStamp = timeMillis;

            pendingOccurrences.add(occurrence);

            // Update any constraints that are still active
            for (Map.Entry<ConstraintEntity, List<OccurrenceEntity>> entry : occurrencesMap.entrySet()) {
                ConstraintEntity constraint = entry.getKey();
                if (constraint != null && id.equals(constraint.constraintId)) {
                    entry.getValue().add(occurrence);
                }
            }
        }

        // Save to database
        executor.execute(new Runnable() {
            @Override
            public void run() {
                writePendingOccurrences();
            }
        });
    }

    @NonNull
    private Collection<ConstraintEntity> fetchConstraints(@Nullable Collection<String> constraintIds) {
        if (constraintIds == null || constraintIds.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<ConstraintEntity> constraints = dao.getConstraints(constraintIds);

        for (ConstraintEntity constraint : constraints) {
            List<OccurrenceEntity> occurrences = dao.getOccurrences(constraint.constraintId);
            synchronized (lock) {
                for (OccurrenceEntity entity : pendingOccurrences) {
                    if (entity.parentConstraintId.equals(constraint.constraintId)) {
                        occurrences.add(entity);
                    }
                }
                occurrencesMap.put(constraint, occurrences);
            }
        }

        return constraints;
    }

    private void writePendingOccurrences() {
        List<OccurrenceEntity> pending;
        synchronized (lock) {
            pending = new ArrayList<>(pendingOccurrences);
            pendingOccurrences.clear();
        }

        for (OccurrenceEntity occurrence : pending) {
            try {
                dao.insert(occurrence);
            } catch (SQLiteException e) {
                Logger.verbose(e);
            }
        }
    }

    private boolean isConstraintOverLimit(@NonNull ConstraintEntity constraint) {
        List<OccurrenceEntity> occurrences = occurrencesMap.get(constraint);

        if (occurrences == null || occurrences.size() < constraint.count) {
            return false;
        }

        long timeSinceOccurrence = clock.currentTimeMillis() - occurrences.get(occurrences.size() - constraint.count).timeStamp;
        return timeSinceOccurrence <= constraint.range;
    }

    @NonNull
    private Set<String> getConstraintIds(@NonNull Collection<ConstraintEntity> constraints) {
        Set<String> constraintIds = new HashSet<>();
        for (ConstraintEntity constraint : constraints) {
            constraintIds.add(constraint.constraintId);
        }
        return constraintIds;
    }

}
