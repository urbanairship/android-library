/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.display

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RestrictTo
import java.util.UUID

/**
 * A display args loader will store a reference to the
 * layout args and restore the instance when it to when
 * loading from a parcel without actually placing the args
 * in a parcel.
 *
 * This only works in a single process.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgsLoader : Parcelable {

    private val id: String?

    private constructor(id: String?) {
        this.id = id
    }

    private constructor(parcel: Parcel) {
        this.id = parcel.readString()
    }

    public fun dispose() {
        cached.remove(id)
    }

    @Throws(LoadException::class)
    public fun getDisplayArgs(): DisplayArgs {
        return cached[id] ?: run {
            throw LoadException("Layout args no longer available")
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
    }

    public companion object {

        private val cached = mutableMapOf<String, DisplayArgs>()

        @JvmField
        public val CREATOR: Parcelable.Creator<DisplayArgsLoader> =
            object : Parcelable.Creator<DisplayArgsLoader> {
                override fun createFromParcel(`in`: Parcel): DisplayArgsLoader {
                    return DisplayArgsLoader(`in`)
                }

                override fun newArray(size: Int): Array<DisplayArgsLoader> {
                    return ArrayList<DisplayArgsLoader>(size).toTypedArray()
                }

            }

        /**
         * Creates a new layout args loader. The args will be cached, to remove them from
         * the cache call `dispose` on the instance.
         *
         * @param args The layout args.
         * @return A layout args loader.
         */
        public fun newLoader(args: DisplayArgs): DisplayArgsLoader {
            val loaderId = UUID.randomUUID().toString()
            cached.put(loaderId, args)
            return DisplayArgsLoader(loaderId)
        }
    }

    public class LoadException public constructor(message: String) : Exception(message)
}
