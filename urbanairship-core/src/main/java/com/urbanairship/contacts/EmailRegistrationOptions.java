/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Email channel registration options.
 */
public class EmailRegistrationOptions implements JsonSerializable {

    public final static String TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in";
    public final static String COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in";
    public final static String PROPERTIES_KEY = "properties";
    public final static String DOUBLE_OPT_IN_KEY = "double_opt_in";

    private final long transactionalOptedIn;
    private final long commercialOptedIn;
    private final boolean doubleOptIn;

    @Nullable
    private final JsonMap properties;

    private EmailRegistrationOptions(@Nullable long transactionalOptedIn,
                                     @Nullable long commercialOptedIn,
                                     @Nullable JsonMap properties,
                                     boolean doubleOptIn) {
        this.transactionalOptedIn = transactionalOptedIn;
        this.commercialOptedIn = commercialOptedIn;
        this.properties = properties;
        this.doubleOptIn = doubleOptIn;
    }

    /**
     * Commercial registration options.
     *
     * @param commercialOptedIn The commercial opted in date.
     * @param transactionalOptedIn The transactional opted in date.
     * @param properties The optional properties.
     * @return The registration options.
     */
    @NonNull
    public static EmailRegistrationOptions commercialOptions(@Nullable Date commercialOptedIn,
                                                             @Nullable Date transactionalOptedIn,
                                                             @Nullable JsonMap properties) {
        return new EmailRegistrationOptions(
                transactionalOptedIn == null ? -1 : transactionalOptedIn.getTime(),
                commercialOptedIn == null ? -1 : commercialOptedIn.getTime(),
                properties,
                false
        );
    }

    /**
     * Email registration options.
     *
     * @param transactionalOptedIn The transactional opted in date.
     * @param properties The optional properties.
     * @param doubleOptIn {@code true} to enable double opt-in, otherwise {@code false}.
     * @return The registration options.
     */
    @NonNull
    public static EmailRegistrationOptions options(@Nullable Date transactionalOptedIn,
                                                   @Nullable JsonMap properties,
                                                   boolean doubleOptIn) {
        return new EmailRegistrationOptions(
                transactionalOptedIn == null ? -1 : transactionalOptedIn.getTime(),
                -1,
                properties,
                doubleOptIn
        );
    }

    /**
     * Email registration options.
     *
     * @param properties The optional properties.
     * @param doubleOptIn {@code true} to enable double opt-in, otherwise {@code false}.
     * @return The registration options.
     */
    @NonNull
    public static EmailRegistrationOptions options(@Nullable JsonMap properties,
                                                   boolean doubleOptIn) {
        return new EmailRegistrationOptions(
            -1,
            -1,
            properties,
            doubleOptIn
        );
    }

    @Nullable
    JsonMap getProperties() {
        return properties;
    }
    long getCommercialOptedIn() {
        return commercialOptedIn;
    }

    long getTransactionalOptedIn() {
        return transactionalOptedIn;
    }

    boolean isDoubleOptIn() {
        return doubleOptIn;
    }

    @NonNull
    static EmailRegistrationOptions fromJson(@NonNull JsonValue value) {
        JsonMap map = value.optMap();
        long commercialOptedIn = map.opt(COMMERCIAL_OPTED_IN_KEY).getLong(-1);
        long transactionalOptedIn = map.opt(TRANSACTIONAL_OPTED_IN_KEY).getLong(-1);
        ;
        JsonMap properties = map.opt(PROPERTIES_KEY).getMap();
        boolean doubleOptIn = map.opt(DOUBLE_OPT_IN_KEY).getBoolean(false);

        return new EmailRegistrationOptions(transactionalOptedIn, commercialOptedIn, properties, doubleOptIn);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(TRANSACTIONAL_OPTED_IN_KEY, transactionalOptedIn)
                      .put(COMMERCIAL_OPTED_IN_KEY, commercialOptedIn)
                      .put(PROPERTIES_KEY, properties)
                      .put(DOUBLE_OPT_IN_KEY, doubleOptIn)
                      .build()
                      .toJsonValue();
    }

}
