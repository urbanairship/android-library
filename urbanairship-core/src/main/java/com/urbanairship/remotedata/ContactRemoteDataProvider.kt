/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import android.net.Uri
import com.urbanairship.PreferenceDataStore
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import java.util.Locale

internal class ContactRemoteDataProvider(
    context: Context,
    preferenceDataStore: PreferenceDataStore,
    config: AirshipRuntimeConfig,
    private val contact: Contact,
    private val apiClient: RemoteDataApiClient,
    private val urlFactory: RemoteDataUrlFactory
) : RemoteDataProvider(
    source = RemoteDataSource.CONTACT,
    remoteDataStore = RemoteDataStore(context, config.configOptions.appKey, "ua_remotedata_contact.db"),
    preferenceDataStore = preferenceDataStore,
    defaultEnabled = false
) {
    override fun isRemoteDataInfoUpToDate(
        remoteDataInfo: RemoteDataInfo,
        locale: Locale,
        randomValue: Int
    ): Boolean {
        if (remoteDataInfo.source != RemoteDataSource.CONTACT) {
            return false
        }

        val contactIdUpdate = contact.currentContactIdUpdate

        if (contactIdUpdate == null || !contactIdUpdate.isStable || contactIdUpdate.contactId != remoteDataInfo.contactId) {
            return false
        }

        val url = createUrl(contactIdUpdate.contactId, locale, randomValue) ?: return false
        return url.toString() == remoteDataInfo.url
    }

    override suspend fun fetchRemoteData(
        locale: Locale,
        randomValue: Int,
        lastRemoteDataInfo: RemoteDataInfo?
    ): RequestResult<RemoteDataApiClient.Result> {
        val contactId = contact.stableContactInfo().contactId
        val url = createUrl(contactId, locale, randomValue)
        var lastModified: String? = null
        if (lastRemoteDataInfo?.url == url.toString()) {
            lastModified = lastRemoteDataInfo.lastModified
        }

        return apiClient.fetch(url, RequestAuth.ContactTokenAuth(contactId), lastModified) {
            RemoteDataInfo(
                url = url.toString(),
                lastModified = it,
                source = RemoteDataSource.CONTACT,
                contactId = contactId
            )
        }
    }

    private fun createUrl(contactId: String, locale: Locale, randomValue: Int): Uri? {
        return urlFactory.createContactUrl(contactId, locale, randomValue)
    }
}
