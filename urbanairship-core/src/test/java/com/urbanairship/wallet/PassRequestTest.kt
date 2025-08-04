/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestBody
import com.urbanairship.json.JsonValue.Companion.parseString
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PassRequestTest {

    private val requestSession = TestRequestSession()

    @Before
    fun setup() {
        TestApplication.getApplication().testRuntimeConfig?.updateRemoteConfig(
            config = RemoteConfig(
                RemoteAirshipConfig(
                    null, null, "https://wallet-api.urbanairship.com", null
                )
            )
        )
    }

    @Test
    fun testDefaultUrl() {
        val request = PassRequest.newBuilder()
            .setAuth("test_user_name", "test_api_key")
            .setTemplateId("test_template_id")
            .build()

        Assert.assertEquals(
            "https://wallet-api.urbanairship.com/v1/pass/test_template_id",
            request.getPassUrl().toString()
        )
    }

    @Test
    fun testExecute() {
        // Based off of example JSON in http://docs.urbanairship.com/api/wallet.html#create-pass
        val requestJson = """{
            "headers":{
                "expirationDate":{
                   "value":"2014-08-20T9:41-08:00"
                 },
                 "barcodeAltText":{
                    "value":"abc1234567890",
                    "label":"label"
                 },
                 "barcode_value":{
                    "value":"abc1234567890",
                    "label":"label"
                 }
            },
            "publicURL" : {
                "type" : "multiple"
            },
            "tag": "Text Tag",
            "externalId": "id123",
            "fields":{
                "Text":{
                   "value":"Text Value",
                   "label":"Text Label"
                }
            }
        }""".trimIndent()

        val responseJson = """{
           "createdAt":"2016-06-09T18:07:42Z",
           "publicUrl":{
              "path":"https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555",
              "type":"Single"
           },
           "id":"5",
           "status":"not_been_installed"
        }""".trimIndent()


        requestSession.addResponse(HttpURLConnection.HTTP_OK, responseJson)

        val field = Field.newBuilder()
            .setName("Text")
            .setValue("Text Value")
            .setLabel("Text Label")
            .build()

        val passRequestBuilder = PassRequest.newBuilder()
            .setAuth("test_user_name", "test_api_key")
            .setTemplateId("test_template_id")
            .setExpirationDate("2014-08-20T9:41-08:00", null)
            .setBarcodeAltText("abc1234567890", "label")
            .setBarcodeValue("abc1234567890", "label")
            .setTag("Text Tag")
            .setExternalId("id123")
            .addField(field)

        val executor = Executor { command -> command.run() }

        val latch = CountDownLatch(1)
        val callback = object : Callback {
            override fun onResult(pass: Pass) {
                Assert.assertEquals("5", pass.id)
                Assert.assertEquals(
                    Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555"),
                    pass.publicUri
                )
                latch.countDown()
            }

            override fun onError(errorCode: Int) {
                throw RuntimeException()
            }
        }

        val passRequest = PassRequest(
            apiKey = "test_api_key",
            templateId = "test_template_id",
            builder = passRequestBuilder,
            session = requestSession,
            requestExecutor = executor
        )

        passRequest.execute(callback, null)
        shadowOf(Looper.getMainLooper()).idle()

        latch.await()

        Assert.assertEquals(
            requestSession.lastRequest.body,
            RequestBody.Json(parseString(requestJson))
        )
    }

    @Test
    fun testExecuteFail() {
        requestSession.addResponse(HttpURLConnection.HTTP_BAD_REQUEST, null)
        val passRequestBuilder = PassRequest.newBuilder()
            .setAuth("test_user_name", "test_api_key")
            .setTemplateId("test_template_id")

        val executor = Executor { command -> command.run() }

        val passRequest = PassRequest(
            apiKey = "test_api_key",
            templateId = "test_template_id",
            builder = passRequestBuilder,
            session = requestSession,
            requestExecutor = executor)

        val latch = CountDownLatch(1)
        val callback: Callback = object : Callback {
            override fun onResult(pass: Pass) {
                throw RuntimeException()
            }

            override fun onError(errorCode: Int) {
                Assert.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST.toLong(), errorCode.toLong())
                latch.countDown()
            }
        }

        passRequest.execute(callback, null)
        shadowOf(Looper.getMainLooper()).idle()
        latch.await()
    }
}
