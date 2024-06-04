/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.os.Parcel
import android.os.Parcelable
import com.urbanairship.actions.ActionRunner
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonMap
import java.util.UUID

/** Display arguments for in-app messages. */
internal data class InAppDisplayArgs<T: InAppMessageDisplayContent>(
    val displayContent: T,
    val assets: AirshipCachedAssets?,
    val displayListener: InAppMessageDisplayListener,
    val extras: JsonMap? = null,
    val actionRunner: ActionRunner
)

/**
 * Stores a reference to the display args for an in-app message and restore it to the instance when
 * loading from a parcel, without actually placing the args in a parcel.
 *
 * This only works in a single process.
 */
internal class InAppDisplayArgsLoader private constructor(
    private val id: String?
) : Parcelable {

    private constructor(parcel: Parcel): this(parcel.readString())

    class LoadException(message: String?) : Exception(message)

    @Throws(LoadException::class)
    fun <T: InAppMessageDisplayContent> load(): InAppDisplayArgs<T> {
        @Suppress("UNCHECKED_CAST")
        val cached = cached[id] as? InAppDisplayArgs<T>

        return cached ?: throw LoadException("In-app display args no longer available")
    }

    fun dispose() {
        cached.remove(id)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeString(id)

    companion object {
        private val cached: MutableMap<String?, InAppDisplayArgs<*>> = HashMap()

        @JvmField
        val CREATOR: Parcelable.Creator<InAppDisplayArgsLoader> =
            object : Parcelable.Creator<InAppDisplayArgsLoader> {
                override fun createFromParcel(parcel: Parcel): InAppDisplayArgsLoader {
                    return InAppDisplayArgsLoader(parcel)
                }

                override fun newArray(size: Int): Array<InAppDisplayArgsLoader?> {
                    return arrayOfNulls(size)
                }
            }

        /**
         * Creates a new layout args loader. The args will be cached, to remove them from
         * the cache call `dispose` on the instance.
         *
         * @param args The layout args.
         * @return A layout args loader.
         */
        fun <T: InAppMessageDisplayContent> newLoader(
            args: InAppDisplayArgs<T>
        ): InAppDisplayArgsLoader {
            val loaderId = UUID.randomUUID().toString()
            cached[loaderId] = args
            return InAppDisplayArgsLoader(loaderId)
        }
    }
}
