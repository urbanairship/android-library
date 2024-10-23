package com.urbanairship.messagecenter.util

import android.os.Parcel
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import kotlinx.parcelize.Parceler

internal object JsonMapParceler : Parceler<JsonMap?> {

    @Throws(JsonException::class)
    override fun create(parcel: Parcel): JsonMap? {
        val json = parcel.readString()
        return JsonValue.parseString(json).map
    }

    override fun JsonMap?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.toString())
    }
}
