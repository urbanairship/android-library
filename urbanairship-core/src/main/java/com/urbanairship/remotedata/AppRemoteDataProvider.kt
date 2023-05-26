/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import android.net.Uri
import com.urbanairship.PreferenceDataStore
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import java.util.Locale

internal class AppRemoteDataProvider(
    context: Context,
    preferenceDataStore: PreferenceDataStore,
    config: AirshipRuntimeConfig,
    private val apiClient: RemoteDataApiClient,
    private val urlFactory: RemoteDataUrlFactory
) : RemoteDataProvider(
    source = RemoteDataSource.APP,
    remoteDataStore = RemoteDataStore(context, config.configOptions.appKey, "ua_remotedata.db"),
    preferenceDataStore = preferenceDataStore,
    defaultEnabled = true
) {

    init {
        // Fixes  17.x -> 16.x -> 17.x issue
        if (preferenceDataStore.isSet(LAST_REFRESH_METADATA)) {
            preferenceDataStore.remove(LAST_REFRESH_METADATA)
            clearLastRefreshState()
        }
    }

    companion object {
        const val LAST_REFRESH_METADATA = "com.urbanairship.remotedata.LAST_REFRESH_METADATA"
    }

    override fun isRemoteDataInfoUpToDate(
        remoteDataInfo: RemoteDataInfo,
        locale: Locale,
        randomValue: Int
    ): Boolean {
        val url = createUrl(locale, randomValue) ?: return false
        return RemoteDataSource.APP == remoteDataInfo.source && url.toString() == remoteDataInfo.url
    }

    override suspend fun fetchRemoteData(
        locale: Locale,
        randomValue: Int,
        lastRemoteDataInfo: RemoteDataInfo?
    ): RequestResult<RemoteDataApiClient.Result> {
        val url = createUrl(locale, randomValue)
        var lastModified: String? = null
        if (lastRemoteDataInfo?.url == url.toString()) {
            lastModified = lastRemoteDataInfo.lastModified
        }

        return apiClient.fetch(url, RequestAuth.BasicAppAuth, lastModified) {
            RemoteDataInfo(
                url = url.toString(),
                lastModified = it,
                source = RemoteDataSource.APP
            )
        }
    }

    private fun createUrl(locale: Locale, randomValue: Int): Uri? {
        return urlFactory.createAppUrl(locale, randomValue)
    }
}
