/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import android.os.Bundle
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.core.app.RemoteInput

/**
 * Remote Input that stores the resource ID instead of a String.
 */
public class LocalizableRemoteInput private constructor(builder: Builder) {

    /**
     * The result key.
     */
    public val resultKey: String = builder.resultKey

    /**
     * The label ID.
     */
    public val label: Int = builder.labelId

    /**
     * Possible input choices. This can be null if there are no choices to present.
     */
    public val choices: IntArray? =  builder.choices

    /**
     * The extras.
     */
    public val extras: Bundle = builder.extras

    /**
     * The allowFreeFormInput boolean value.
     * `true` if free form input is allowed, otherwise `false`.
     */
    public val allowFreeFormInput: Boolean = builder.allowFreeFormInput

    private val choicesArray: Int = builder.choicesArray

    /**
     * Creates the remote input.
     *
     * @param context The application context.
     * @return The remote input.
     */
    public fun createRemoteInput(context: Context): RemoteInput {
        val builder = RemoteInput.Builder(resultKey)
            .setAllowFreeFormInput(allowFreeFormInput)
            .addExtras(extras)

        choices?.let { value ->
            val mapped = value.map { context.getText(it) }
            builder.setChoices(mapped.toTypedArray())
        }

        if (choicesArray != 0) {
            val replyChoices = context.resources.getStringArray(choicesArray)
            builder.setChoices(replyChoices)
        }

        if (label != 0) {
            builder.setLabel(context.getText(label))
        }

        return builder.build()
    }

    /**
     * Builds the LocalizableRemoteInput.
     */
    public class Builder public constructor(
        internal val resultKey: String
    ) {

        public var labelId: Int = 0
            private set
        public var choices: IntArray? = null
            private set
        public val extras: Bundle = Bundle()
        public var allowFreeFormInput: Boolean = false
            private set
        public var choicesArray: Int = 0
            private set

        /**
         * Set the label ID value.
         *
         * @param labelId An int value.
         * @return The builder with the label ID set.
         */
        public fun setLabel(@StringRes labelId: Int): Builder {
            return this.also { it.labelId = labelId }
        }

        /**
         * Set the choices value.
         *
         * @param choices An int array.
         * @return The builder with the choices set.
         */
        public fun setChoices(@ArrayRes choices: Int): Builder {
            return this.also {
                it.choices = null
                it.choicesArray = choices
            }
        }

        /**
         * Set the [allowFreeFormInput] value.
         *
         * @param allowFreeFormInput A boolean value.
         * @return The builder with the allowFreeFormInput set.
         */
        public fun setAllowFreeFormInput(allowFreeFormInput: Boolean): Builder {
            return this.also { it.allowFreeFormInput = allowFreeFormInput }
        }

        /**
         * Set the extras value.
         *
         * @param extras A bundle value.
         * @return The builder with the extras set.
         */
        public fun addExtras(extras: Bundle?): Builder {
            return this.also { builder ->
                extras?.let { builder.extras.putAll(it) }
            }
        }

        /**
         * Builds and returns a LocalizableRemoteInput.
         *
         * @return The LocalizableRemoteInput.
         */
        public fun build(): LocalizableRemoteInput {
            return LocalizableRemoteInput(this)
        }
    }
}
