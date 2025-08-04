/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Looper
import androidx.annotation.Size
import com.urbanairship.AirshipExecutors.newSerialExecutor
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.config.UrlBuilder
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth.BasicAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestSession
import com.urbanairship.http.ResponseParser
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.UAHttpStatusUtil
import java.util.concurrent.Executor

/**
 * Defines a request to fetch a [Pass].
 */
public class PassRequest internal constructor(
    private val apiKey: String,
    private val templateId: String,
    builder: Builder,
    private val session: RequestSession = UAirship.shared().runtimeConfig.requestSession,
    private val requestExecutor: Executor = DEFAULT_REQUEST_EXECUTOR
) {

    private val userName: String? = builder.userName
    private val fields: List<Field> = builder.fields
    private val headers: List<Field> = builder.headers
    private val tag: String? = builder.tag
    private val externalId: String? = builder.externalId

    private var requestCallback: CancelableCallback? = null

    /**
     * Executes the request to fetch the [Pass]. Must be called on the
     * UI thread.
     *
     * @param callback A callback for the result.
     */
    @SuppressLint("NewApi")
    public fun execute(callback: Callback) {
        execute(callback, null)
    }

    /**
     * Executes the request to fetch the [Pass]. Must be called on the
     * UI thread.
     *
     * @param callback A callback for the result.
     * @param looper The looper used for executing the result callback. Defaults to that
     * of the calling thread if null.
     */
    @SuppressLint("NewApi")
    public fun execute(callback: Callback, looper: Looper?) {
        check(requestCallback == null) { "PassRequest can only be executed once." }

        requestCallback = CancelableCallback(callback, looper)
        val requestRunnable = object : Runnable {
            override fun run() {
                UALog.i("Requesting pass %s", templateId)
                val url = this@PassRequest.getPassUrl() ?: run {
                    UALog.e("PassRequest - Invalid pass URL")
                    requestCallback?.setResult(-1, null)
                    return
                }

                val fieldsJson = JsonMap.newBuilder()
                fields.forEach { fieldsJson.putOpt(it.name, it) }

                var headersJson: JsonMap? = null
                if (headers.isNotEmpty()) {
                    val builder = JsonMap.newBuilder()
                    headers.forEach { builder.putOpt(it.name, it) }
                    headersJson = builder.build()
                }

                val body = jsonMapOf(
                    HEADERS_KEY to headersJson,
                    FIELDS_KEY to fieldsJson.build(),
                    TAG_KEY to tag,
                    PUBLIC_URL_KEY to jsonMapOf(PUBLIC_URL_TYPE_KEY to "multiple"),
                    EXTERNAL_ID_KEY to externalId
                )

                val headers = mapOf(API_KEY_QUERY_PARAM to API_REVISION)
                val auth = userName?.let { BasicAuth(it, apiKey) }
                val httpRequest = Request(url, "POST", auth, RequestBody.Json(body), headers)

                UALog.d("Requesting pass %s with payload: %s", url, body)
                try {
                    val response = session.execute(
                        request = httpRequest,
                        parser = ResponseParser { status: Int, _, responseBody ->
                            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                                return@ResponseParser null
                            }

                            Pass.parsePass(JsonValue.parseString(responseBody))
                        })

                    UALog.d("Pass %s request finished with status %s", templateId, response.status)
                    requestCallback?.setResult(response.status, response.result)
                } catch (e: RequestException) {
                    UALog.e(e, "PassRequest - Request failed")
                    requestCallback?.setResult(-1, null)
                }

                // Notify the result
                requestCallback?.run()
            }
        }

        requestExecutor.execute(requestRunnable)
    }

    /**
     * Cancels the requests.
     */
    public fun cancel() {
        requestCallback?.cancel()
    }

    /**
     * Gets the pass request URL.
     */
    public fun getPassUrl(): Uri? {
        val urlBuilder: UrlBuilder = UAirship.shared().runtimeConfig.walletUrl
            .appendEncodedPath(PASS_PATH)
            .appendEncodedPath(templateId)

        // User name support requires apiKey to be set on the URL.
        if (userName == null) {
            urlBuilder.appendQueryParameter(API_KEY_QUERY_PARAM, apiKey)
        }

        return urlBuilder.build()

    }

    override fun toString(): String {
        return "PassRequest{ templateId: $templateId, fields: $fields, tag: $tag, " +
                "externalId: $externalId, headers: $headers }"
    }

    /**
     * Builds the [PassRequest] object.
     */
    public class Builder public constructor() {

        public var apiKey: String? = null
            private set
        public var templateId: String? = null
            private set
        public val fields: MutableList<Field> = mutableListOf()
        public val headers: MutableList<Field> = mutableListOf()
        public var tag: String? = null
            private set
        public var externalId: String? = null
            private set
        public var userName: String? = null
            private set

        /**
         * Sets the request auth.
         *
         * @param userName The request user name.
         * @param token The request token.
         * @return Builder object.
         */
        public fun setAuth(userName: String, token: String): Builder {
            this.apiKey = token
            this.userName = userName
            return this
        }

        /**
         * Sets the Template ID.
         *
         * @param templateId The ID of the template.
         * @return Builder object.
         */
        public fun setTemplateId(@Size(min = 1) templateId: String): Builder {
            this.templateId = templateId
            return this
        }

        /**
         * Adds field information for the pass.
         *
         * @param field The field instance.
         * @return Builder object.
         */
        public fun addField(field: Field): Builder {
            fields.add(field)
            return this
        }

        /**
         * Sets the expirationDate field.
         *
         * @param value The expiration date value.
         * @param label The expiration date label.
         * @return Builder object.
         */
        public fun setExpirationDate(value: String, label: String?): Builder {
            val field = Field.newBuilder()
                .setName(FIELD_NAME_EXPIRATION_DATE)
                .setValue(value)
                .setLabel(label)
                .build()

            headers.add(field)
            return this
        }

        /**
         * Sets the barcode_value field.
         *
         * @param value The barcode_value value.
         * @param label The barcode_value label.
         * @return Builder object.
         */
        public fun setBarcodeValue(value: String, label: String): Builder {
            val field = Field.newBuilder()
                .setName(FIELD_NAME_BARCODE_VALUE)
                .setValue(value)
                .setLabel(label)
                .build()

            headers.add(field)
            return this
        }

        /**
         * Sets the barcodeAltText field.
         *
         * @param value The barcodeAltText value.
         * @param label The barcodeAltText label.
         * @return Builder object.
         */
        public fun setBarcodeAltText(value: String, label: String): Builder {
            val field = Field.newBuilder()
                .setName(FIELD_NAME_BARCODE_ALT_TEXT)
                .setValue(value)
                .setLabel(label)
                .build()

            headers.add(field)
            return this
        }

        /**
         * Sets the pass tag.
         *
         * @param tag The pass tag.
         * @return Builder object.
         */
        public fun setTag(tag: String?): Builder {
            this.tag = tag
            return this
        }

        /**
         * Sets the external ID.
         *
         * @param externalId The external ID.
         * @return Builder object.
         */
        public fun setExternalId(externalId: String?): Builder {
            this.externalId = externalId
            return this
        }

        /**
         * Builds the [PassRequest].
         *
         * @return A @link PassRequest} instance.
         * @throws IllegalStateException if the apiKey or templateId is
         * null or empty.
         */
        public fun build(): PassRequest {
            val apiKey = this.apiKey ?: throw IllegalStateException("The apiKey is missing.")
            val templateId = this.templateId ?: throw IllegalStateException("The templateId is missing.")

            return PassRequest(
                apiKey = apiKey,
                templateId = templateId,
                builder = this
            )
        }

        internal companion object {
            private const val FIELD_NAME_EXPIRATION_DATE = "expirationDate"
            private const val FIELD_NAME_BARCODE_VALUE = "barcode_value"
            private const val FIELD_NAME_BARCODE_ALT_TEXT = "barcodeAltText"
        }
    }

    public companion object {

        private val DEFAULT_REQUEST_EXECUTOR: Executor = newSerialExecutor()

        private const val PASS_PATH: String = "v1/pass"
        private const val API_KEY_QUERY_PARAM: String = "api_key"

        private const val API_REVISION: String = "1.2"
        private const val FIELDS_KEY: String = "fields"
        private const val HEADERS_KEY: String = "headers"
        private const val PUBLIC_URL_KEY: String = "publicURL"
        private const val PUBLIC_URL_TYPE_KEY: String = "type"
        private const val TAG_KEY: String = "tag"
        private const val EXTERNAL_ID_KEY: String = "externalId"

        /**
         * Creates a new Builder instance.
         *
         * @return The new Builder instance.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
