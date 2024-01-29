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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Frequency limit manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FrequencyLimitManager {
    private final Map<String, List<OccurrenceEntity>> occurrencesMap = new HashMap<>();
    private final Map<String, ConstraintEntity> constraintEntityMap = new HashMap<>();
    private final List<OccurrenceEntity> pendingOccurrences = new ArrayList<>();
    private final Clock clock;
    private final Object lock = new Object();
    private final FrequencyLimitDao dao;
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
    public Future<FrequencyChecker> getFrequencyChecker(@NonNull final Collection<String> constraintIds) {
        final PendingResult<FrequencyChecker> pendingResult = new PendingResult<>();
        executor.execute(() -> {
            for (String constraintId : constraintIds) {
                synchronized (lock) {
                    if (constraintEntityMap.containsKey(constraintId)) {
                        continue;
                    }
                }

                List<OccurrenceEntity> occurrenceEntities = dao.getOccurrences(constraintId);
                List<ConstraintEntity> constraintEntities = dao.getConstraints(Collections.singletonList(constraintId));
                if (constraintEntities.size() != 1) {
                    pendingResult.setResult(null);
                    return;
                }

                synchronized (lock) {
                    constraintEntityMap.put(constraintId, constraintEntities.get(0));
                    occurrencesMap.put(constraintId, occurrenceEntities);
                }
            }

            pendingResult.setResult(new FrequencyChecker() {
                @Override
                public boolean isOverLimit() {
                    return FrequencyLimitManager.this.isOverLimit(constraintIds);
                }

                @Override
                public boolean checkAndIncrement() {
                    return FrequencyLimitManager.this.checkAndIncrement(constraintIds);
                }
            });
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
        executor.execute(() -> {
            try {
                Collection<ConstraintEntity> constraintEntities = dao.getConstraints();

                Map<String, ConstraintEntity> entityMap = new HashMap<>();
                for (ConstraintEntity entity : constraintEntities) {
                    entityMap.put(entity.constraintId, entity);
                }

                for (FrequencyConstraint constraint : constraints) {
                    ConstraintEntity entity = new ConstraintEntity();
                    entity.constraintId = constraint.getId();
                    entity.count = constraint.getCount();
                    entity.range = constraint.getRange();

                    ConstraintEntity existing = entityMap.remove(constraint.getId());
                    if (existing != null) {
                        if (existing.range != entity.range) {
                            dao.delete(existing);
                            dao.insert(entity);

                            synchronized(lock) {
                                occurrencesMap.put(constraint.getId(), new ArrayList<>());

                                if (entityMap.containsKey(constraint.getId())) {
                                    constraintEntityMap.put(constraint.getId(), entity);
                                }
                            }
                        } else {
                            dao.update(entity);

                            synchronized(lock) {
                                if (entityMap.containsKey(constraint.getId())) {
                                    constraintEntityMap.put(constraint.getId(), entity);
                                }
                            }
                        }
                    } else {
                        dao.insert(entity);
                    }
                }

                dao.delete(entityMap.keySet());
                pendingResult.setResult(true);
            } catch (Exception e) {
                Logger.error(e, "Failed to update constraints");
                pendingResult.setResult(false);
            }
        });

        return pendingResult;
    }

    private boolean checkAndIncrement(@NonNull Collection<String> constraintIds) {
        if (constraintIds.isEmpty()) {
            return true;
        }

        synchronized (lock) {
            if (isOverLimit(constraintIds)) {
                return false;
            }
            recordOccurrence(constraintIds);
            return true;
        }
    }

    private boolean isOverLimit(@NonNull Collection<String> constraintIds) {
        if (constraintIds.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            for (String constraintId : constraintIds) {
                if (isConstraintOverLimit(constraintId)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void recordOccurrence(@NonNull Collection<String> constraintIds) {
        if (constraintIds.isEmpty()) {
            return;
        }

        long timeMillis = clock.currentTimeMillis();

        synchronized (lock) {
            for (String id : constraintIds) {
                OccurrenceEntity occurrence = new OccurrenceEntity();
                occurrence.parentConstraintId = id;
                occurrence.timeStamp = timeMillis;

                pendingOccurrences.add(occurrence);

                if (occurrencesMap.get(id) == null) {
                    occurrencesMap.put(id, new ArrayList<>());
                }
                occurrencesMap.get(id).add(occurrence);
            }
        }

        // Save to database
        executor.execute(this::writePendingOccurrences);
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
                synchronized (lock) {
                    pendingOccurrences.add(occurrence);
                }
            }
        }
    }

    private boolean isConstraintOverLimit(@NonNull String constraintId) {
        synchronized (lock) {
            List<OccurrenceEntity> occurrences = occurrencesMap.get(constraintId);
            ConstraintEntity constraint = constraintEntityMap.get(constraintId);

            if (constraint == null || occurrences == null || occurrences.size() < constraint.count) {
                return false;
            }

            // Sort the occurrences by timestamp
            Collections.sort(occurrences, new OccurrenceEntity.Comparator());
            long timeSinceOccurrence = clock.currentTimeMillis() - occurrences.get(occurrences.size() - constraint.count).timeStamp;
            return timeSinceOccurrence <= constraint.range;
        }
    }
}
