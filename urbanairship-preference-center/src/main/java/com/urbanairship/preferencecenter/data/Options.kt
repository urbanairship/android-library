package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonMap
import com.urbanairship.preferencecenter.util.jsonMapOf

data class Options(
    val mergeChannelDataToContact: Boolean
) {
    companion object {
        private const val KEY_MERGE_CHANNEL_DATA_TO_CONTACT = "merge_channel_data_to_contact"

        /**
         * Parses a `JsonMap` into a `Options` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): Options =
            Options(
                mergeChannelDataToContact = json.opt(KEY_MERGE_CHANNEL_DATA_TO_CONTACT).getBoolean(false)
            )
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_MERGE_CHANNEL_DATA_TO_CONTACT to mergeChannelDataToContact
    )
}
