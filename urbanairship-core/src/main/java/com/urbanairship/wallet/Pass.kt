/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.urbanairship.UALog
import com.urbanairship.json.JsonValue

/**
 * Pass representing either an offer or loyalty wallet object.
 *
 *
 * Once a pass is obtained, call [.requestToSavePass].
 */
public class Pass : Parcelable {

    /**
     * The pass URI to link to Android Pay.
     */
    public val publicUri: Uri

    /**
     * Gets the pass ID.
     */
    @JvmField
    public val id: String?

    /**
     * Default constructor.
     *
     * @param uri The publicly accessible URI for the pass.
     * @param id The pass ID.
     */
    internal constructor(uri: Uri, id: String?) {
        this.publicUri = uri
        this.id = id
    }

    @Throws(IllegalArgumentException::class)
    internal constructor(parcel: Parcel) {
        publicUri = parcel.readParcelable(Uri::class.java.classLoader)
            ?: throw IllegalArgumentException("publicUri cannot be null")
        id = parcel.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(publicUri, flags)
        dest.writeString(id)
    }

    override fun describeContents(): Int {
        return 0
    }

    /**
     * Requests to save the pass.
     *
     * @param context The application context.
     */
    public fun requestToSavePass(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(publicUri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    public companion object {

        private const val PUBLIC_URL_KEY = "publicUrl"
        private const val PUBLIC_URL_PATH_KEY = "path"
        private const val ID_KEY = "id"

        /**
         * @hide
         */
        public val CREATOR: Parcelable.Creator<Pass> = object : Parcelable.Creator<Pass> {
            override fun createFromParcel(`in`: Parcel): Pass {
                return Pass(`in`)
            }

            override fun newArray(size: Int): Array<Pass?> {
                return arrayOfNulls(size)
            }
        }

        /**
         * Parse a pass.
         *
         * @param pass The pass response.
         * @return A [Pass].
         */
        @JvmStatic
        public fun parsePass(pass: JsonValue): Pass? {
            val id = pass.optMap().opt(ID_KEY).string
            val uriString = pass.optMap().opt(PUBLIC_URL_KEY).optMap().opt(PUBLIC_URL_PATH_KEY).string
            if (uriString.isNullOrEmpty()) {
                UALog.e("Pass - unable to parse URI from %s", pass)
                return null
            }

            val url = Uri.parse(uriString)
            return Pass(url, id)
        }
    }
}
