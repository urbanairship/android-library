/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import com.urbanairship.http.RequestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class CachingSmsValidatorApiClient(
    private val client: SmsValidatorApiInterface,
    private val maxCacheEntries: Int = 10
): SmsValidatorApiInterface {

    private val _cache = MutableStateFlow<List<Entry>>(emptyList())

    override suspend fun validateSmsWithPrefix(
        msisdn: String,
        prefix: String
    ): RequestResult<SmsValidatorApiClient.Result> {
        cachedResultWithPrefix(msisdn, prefix)?.let { return it }

        val result = client.validateSmsWithPrefix(msisdn, prefix)
        cacheResult(result, msisdn = msisdn, prefix = prefix)
        return result
    }

    override suspend fun validateSmsWithSender(
        msisdn: String,
        sender: String
    ): RequestResult<SmsValidatorApiClient.Result> {
        cachedResultWithSender(msisdn, sender)?.let { return it }

        val result = client.validateSmsWithSender(msisdn, sender)
        cacheResult(result, msisdn = msisdn, sender = sender)
        return result
    }

    private fun cachedResultWithSender(
        msisdn: String,
        sender: String
    ): RequestResult<SmsValidatorApiClient.Result>? {
        return _cache.value.firstOrNull { it.msisdn == msisdn && it.sender == sender }?.result
    }

    private fun cachedResultWithPrefix(
        msisdn: String,
        prefix: String
    ): RequestResult<SmsValidatorApiClient.Result>? {
        return _cache.value.firstOrNull { it.msisdn == msisdn && it.prefix == prefix }?.result
    }

    private fun cacheResult(
        result: RequestResult<SmsValidatorApiClient.Result>,
        msisdn: String,
        prefix: String? = null,
        sender: String? = null
    ) {
        if (!result.isSuccessful) {
            return
        }

        val entry = Entry(msisdn = msisdn, sender = sender, prefix = prefix, result = result)

        _cache.update { current ->
            current
                .toMutableList()
                .also { result ->
                    result.removeAll { it == entry }
                    result.add(entry)
                    if (result.size > maxCacheEntries) {
                        result.removeAt(0)
                    }
                }
                .toList()
        }
    }

    private data class Entry(
        val msisdn: String,
        val sender: String? = null,
        val prefix: String? = null,
        val result: RequestResult<SmsValidatorApiClient.Result>
    )
}