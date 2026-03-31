package com.urbanairship.iam.assets

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import com.urbanairship.android.layout.assets.AirshipCachedAssets

internal class EmptyAirshipCachedAssets : AirshipCachedAssets {
    override fun cacheUri(remoteUrl: String): Uri? = null
    override fun isCached(remoteUrl: String): Boolean = false
    override fun getMediaSize(remoteUrl: String): Size = Size(-1, -1)

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { }

    companion object CREATOR: Parcelable.Creator<EmptyAirshipCachedAssets> {
        override fun createFromParcel(source: Parcel?): EmptyAirshipCachedAssets {
            return EmptyAirshipCachedAssets()
        }

        override fun newArray(size: Int): Array<EmptyAirshipCachedAssets?> = arrayOfNulls(size)
    }
}
