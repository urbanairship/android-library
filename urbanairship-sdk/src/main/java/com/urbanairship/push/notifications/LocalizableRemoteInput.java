/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.ArrayRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.RemoteInput;

/**
 * Remote Input that stores the resource ID instead of a String.
 */
public class LocalizableRemoteInput {

    private final String resultKey;
    private final int labelId;
    private final int[] choices;
    private final Bundle extras;
    private final boolean allowFreeFormInput;
    private final int choicesArray;

    private LocalizableRemoteInput(Builder builder) {
        this.resultKey = builder.resultKey;
        this.labelId = builder.labelId;
        this.choices = builder.choices;
        this.allowFreeFormInput = builder.allowFreeFormInput;
        this.extras = builder.extras;
        this.choicesArray = builder.choicesArray;
    }

    /**
     * Gets the result key.
     *
     * @return The result key as a string.
     */
    public String getResultKey() {
        return resultKey;
    }

    /**
     * Gets the label ID.
     *
     * @return The label ID as an int.
     */
    public int getLabel() {
        return labelId;
    }

    /**
     * Get possible input choices. This can be null if there are no choices to present.
     *
     * @return The choices as an array of int.
     */
    public int[] getChoices() {
        return choices;
    }

    /**
     * Gets the allowFreeFormInput boolean value.
     *
     * @return <code>true</code> if free form input is allowed, otherwise <code>false</code>.
     */
    public boolean getAllowFreeFormInput() {
        return allowFreeFormInput;
    }

    /**
     * Gets the extras.
     *
     * @return The extras as a bundle.
     */
    public Bundle getExtras() {
        return extras;
    }

    /**
     * Creates the remote input.
     *
     * @param context The application context.
     * @return The remote input.
     */
    public RemoteInput createRemoteInput(Context context) {
        RemoteInput.Builder builder = new RemoteInput.Builder(resultKey)
                .setAllowFreeFormInput(allowFreeFormInput)
                .addExtras(extras);

        if (choices != null) {
            CharSequence[] convertedChoices = new CharSequence[choices.length];
            for (int i = 0; i < choices.length; i++) {
                convertedChoices[i] = context.getText(choices[i]);
            }
            builder.setChoices(convertedChoices);
        }

        if (choicesArray != 0) {
            String[] replyChoices = context.getResources().getStringArray(choicesArray);
            builder.setChoices(replyChoices);
        }

        if (labelId != 0) {
            builder.setLabel(context.getText(labelId));
        }

        return builder.build();
    }

    /**
     * Builds the LocalizableRemoteInput.
     */
    public static final class Builder {
        private final String resultKey;
        private int labelId;
        private int[] choices;
        private final Bundle extras = new Bundle();
        private boolean allowFreeFormInput = false;
        private int choicesArray;

        /**
         * Set the result key value.
         *
         * @param resultKey A string value.
         */
        public Builder(@NonNull  String resultKey) {
            this.resultKey = resultKey;
        }

        /**
         * Set the label ID value.
         *
         * @param labelId An int value.
         * @return The builder with the label ID set.
         */
        public Builder setLabel(@StringRes  int labelId) {
            this.labelId = labelId;
            return this;
        }

        /**
         * Set the choices value.
         *
         * @param choices An int array.
         * @return The builder with the choices set.
         */
        public Builder setChoices(@ArrayRes int choices) {
            this.choices = null;
            this.choicesArray = choices;
            return this;
        }

        /**
         * Set the allowFreeFormInput value.
         *
         * @param allowFreeFormInput A boolean value.
         * @return The builder with the allowFreeFormInput set.
         */
        public Builder setAllowFreeFormInput(boolean allowFreeFormInput) {
            this.allowFreeFormInput = allowFreeFormInput;
            return this;
        }

        /**
         * Set the extras value.
         *
         * @param extras A bundle value.
         * @return The builder with the extras set.
         */
        public Builder addExtras(Bundle extras) {
            if (extras != null) {
                this.extras.putAll(extras);
            }
            return this;
        }

        /**
         * Builds and returns a LocalizableRemoteInput.
         *
         * @return The LocalizableRemoteInput.
         */
        public LocalizableRemoteInput build() {
            return new LocalizableRemoteInput(this);
        }
    }
}
