/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.urbanairship.push.notifications;

import android.content.Context;
import android.os.Bundle;
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

    private LocalizableRemoteInput(String resultKey, int labelId, int[] choices,
                        boolean allowFreeFormInput, Bundle extras) {

        this.resultKey = resultKey;
        this.labelId = labelId;
        this.choices = choices;
        this.allowFreeFormInput = allowFreeFormInput;
        this.extras = extras;
    }

    /**
     * Gets the result key.
     * @return The result key as a string.
     */
    public String getResultKey() {
        return resultKey;
    }

    /**
     * Gets the label ID.
     * @return The label ID as an int.
     */
    public int getLabel() {
        return labelId;
    }

    /**
     * Get possible input choices. This can be null if there are no choices to present.
     * @return The choices as an array of int.
     */
    public int[] getChoices() {
        return choices;
    }

    /**
     * Gets the allowFreeFormInput boolean value.
     * @return <code>true</code> if free form input is allowed, otherwise <code>false</code>.
     */
    public boolean getAllowFreeFormInput() {
        return allowFreeFormInput;
    }

    /**
     * Gets the extras.
     * @return The extras as a bundle.
     */
    public Bundle getExtras() {
        return extras;
    }

    /**
     * Creates the remote input.
     * @param context The application context.
     * @return The remote input.
     */
    public RemoteInput createRemoteInput(Context context) {
        RemoteInput.Builder builder = new RemoteInput.Builder(resultKey)
                .addExtras(extras);

        if (choices != null) {
            CharSequence[] convertedChoices = new CharSequence[choices.length];
            for (int i = 0; i < choices.length; i++) {
                convertedChoices[i] = context.getText(choices[i]);
            }
            builder.setChoices(convertedChoices);
        }

        if (labelId >= 0) {
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
        private Bundle extras = new Bundle();
        private boolean allowFreeFormInput = false;

        /**
         * Set the result key value.
         * @param resultKey A string value.
         */
        public Builder(String resultKey) {
            if (resultKey == null) {
                throw new IllegalArgumentException("Result key can't be null");
            }

            this.resultKey = resultKey;
        }

        /**
         * Set the label ID value.
         * @param labelId An int value.
         * @return The builder with the label ID set.
         */
        public Builder setLabel(int labelId) {
            this.labelId = labelId;
            return this;
        }

        /**
         * Set the choices value.
         * @param choices An int array.
         * @return The builder with the choices set.
         */
        public Builder setChoices(int[] choices) {
            this.choices = choices;
            return this;
        }

        /**
         * Set the allowFreeFormInput value.
         * @param allowFreeFormInput A boolean value.
         * @return The builder with the allowFreeFormInput set.
         */
        public Builder setAllowFreeFormInput(boolean allowFreeFormInput) {
            this.allowFreeFormInput = allowFreeFormInput;
            return this;
        }

        /**
         * Set the extras value.
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
         * @return The LocalizableRemoteInput.
         */
        public LocalizableRemoteInput build() {
            return new LocalizableRemoteInput(resultKey, labelId, choices, allowFreeFormInput, extras);
        }
    }
}
