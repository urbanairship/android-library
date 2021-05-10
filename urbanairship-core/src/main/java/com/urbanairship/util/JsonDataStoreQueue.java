/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.arch.core.util.Function;

/**
 * JsonDataStoreQueue is a thread safe storage queue for json serializable items
 * backed by the preference data store.
 *
 * @param <T> The value to be stored.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JsonDataStoreQueue<T> {

    private final PreferenceDataStore dataStore;
    private final String storeKey;
    private final Function<JsonValue, T> deserializer;
    private final Function<T, ? extends JsonSerializable> serializer;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param storeKey The store key.
     * @param serializer The value serializer.
     * @param deserializer The value deserializer.
     */
    public JsonDataStoreQueue(@NonNull PreferenceDataStore dataStore,
                              @NonNull String storeKey,
                              @NonNull Function<T, ? extends JsonSerializable> serializer,
                              @NonNull Function<JsonValue, T> deserializer) {

        this.dataStore = dataStore;
        this.storeKey = storeKey;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    /**
     * Removes all elements.
     */
    public void removeAll() {
        synchronized (storeKey) {
            dataStore.remove(storeKey);
        }
    }

    /**
     * Adds all values into the queue.
     *
     * @param values The values.
     */
    public void addAll(@NonNull List<T> values) {
        if (values.isEmpty()) {
            return;
        }

        synchronized (storeKey) {
            List<JsonValue> jsonValues = dataStore.getJsonValue(storeKey).optList().getList();
            for (T value : values) {
                jsonValues.add(serializer.apply(value).toJsonValue());
            }
            dataStore.put(storeKey, JsonValue.wrapOpt(jsonValues));

        }
    }

    /**
     * Adds a value to the queue.
     *
     * @param value The value.
     */
    public void add(@NonNull T value) {
        synchronized (storeKey) {
            List<JsonValue> jsonValues = dataStore.getJsonValue(storeKey).optList().getList();
            jsonValues.add(serializer.apply(value).toJsonValue());
            dataStore.put(storeKey, JsonValue.wrapOpt(jsonValues));
        }
    }

    /**
     * Pops the next value off the queue.
     *
     * @return The next value or {@code null} if the queue is empty.
     */
    @Nullable
    public T pop() {
        synchronized (storeKey) {
            List<JsonValue> jsonValues = dataStore.getJsonValue(storeKey).optList().getList();
            if (jsonValues.isEmpty()) {
                return null;
            }

            JsonValue value = jsonValues.remove(0);
            if (jsonValues.isEmpty()) {
                dataStore.remove(storeKey);
            } else {
                dataStore.put(storeKey, JsonValue.wrapOpt(jsonValues));
            }

            return deserializer.apply(value);
        }
    }

    /**
     * Peeks the next value.
     *
     * @return The next value or {@code null} if the queue is empty.
     */
    @Nullable
    public T peek() {
        List<JsonValue> jsonValues = dataStore.getJsonValue(storeKey).optList().getList();
        if (jsonValues.isEmpty()) {
            return null;
        }

        JsonValue value = jsonValues.get(0);
        return deserializer.apply(value);
    }

    /**
     * Gets the values as a list.
     *
     * @return The list.
     */
    @NonNull
    public List<T> getList() {
        synchronized (storeKey) {
            List<T> values = new ArrayList<>();
            for (JsonValue value : dataStore.getJsonValue(storeKey).optList()) {
                values.add(deserializer.apply(value));
            }
            return values;
        }
    }

    /**
     * Applies an operation to the queue.
     *
     * @param listOperation The operation.
     */
    public void apply(Function<List<T>, List<T>> listOperation) {
        synchronized (storeKey) {
            List<T> values = getList();
            values = listOperation.apply(values);
            if (values.isEmpty()) {
                dataStore.remove(storeKey);
            } else {
                dataStore.put(storeKey, JsonValue.wrapOpt(values));
            }
        }
    }

}
