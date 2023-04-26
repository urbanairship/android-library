/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import android.util.Log
import com.urbanairship.Logger
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.util.UAHttpStatusUtil

@OpenForTesting
internal class SubscriptionListApiClient constructor(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = runtimeConfig.requestSession.toSuspendingRequestSession()
) {

    /**
     * Fetches the current set of subscriptions for the contact.
     *
     * @return The response.
     * @throws RequestException
     */
    @Throws(RequestException::class)
    suspend fun getSubscriptionLists(contactId: String): RequestResult<Map<String, Set<Scope>>> {
        val url = runtimeConfig.urlConfig.deviceUrl()
            .appendEncodedPath(SUBSCRIPTION_LIST_PATH + contactId).build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey
        )

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.ContactTokenAuth(contactId),
            headers = headers
        )

        Logger.d { "Fetching contact subscription lists for $contactId request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->

            if (UAHttpStatusUtil.inSuccessRange(status)) {
                val json = JsonValue.parseString(responseBody)
                    .requireMap()
                    .require(SUBSCRIPTION_LISTS_KEY)
                    .requireList()

                val subscriptionLists = mutableMapOf<String, MutableSet<Scope>>()
                json.map { entryJson ->
                    val scope = Scope.fromJson(entryJson.optMap().opt(SCOPE_KEY))
                    for (listIdJson in entryJson.optMap().opt(LIST_IDS_KEY).optList()) {
                        val listId = listIdJson.requireString()
                        var scopes = subscriptionLists[listId]
                        if (scopes == null) {
                            scopes = HashSet()
                            subscriptionLists[listId] = scopes
                        }
                        scopes.add(scope)
                    }
                }

                return@execute subscriptionLists.mapValues { it.value.toSet() }.toMap()
            } else {
                return@execute null
            }
        }.also { result ->
            result.log { "Fetching contact subscription lists for $contactId finished with result: $result" }
        }
    }

    private companion object {
        private const val SUBSCRIPTION_LIST_PATH = "api/subscription_lists/contacts/"
        private const val SUBSCRIPTION_LISTS_KEY = "subscription_lists"
        private const val SCOPE_KEY = "scope"
        private const val LIST_IDS_KEY = "list_ids"
    }
}

private fun RequestResult<*>.log(message: () -> String) {
    when {
        this.exception != null -> Logger.log(Log.ERROR, this.exception, message)
        this.isClientError -> Logger.log(Log.ERROR, null, message)
        else -> Logger.log(Log.ERROR, null, message)
    }
}
