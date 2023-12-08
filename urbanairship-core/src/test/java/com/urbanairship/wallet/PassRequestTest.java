/* Copyright Airship and Contributors */

package com.urbanairship.wallet;

import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequestSession;
import com.urbanairship.http.RequestBody;
import com.urbanairship.json.JsonValue;
import com.urbanairship.remoteconfig.RemoteAirshipConfig;
import com.urbanairship.remoteconfig.RemoteConfig;
import com.urbanairship.shadow.ShadowNotificationManagerExtension;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.net.HttpURLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

@Config(
        sdk = 28,
        shadows = { ShadowNotificationManagerExtension.class, ShadowAirshipExecutorsLegacy.class }
)
@LooperMode(LooperMode.Mode.LEGACY)
public class PassRequestTest extends BaseTestCase {

    private TestRequestSession requestSession = new TestRequestSession();

    @Before
    public void setup() {
        TestApplication.getApplication().testRuntimeConfig.updateRemoteConfig(
                new RemoteConfig(
                        new RemoteAirshipConfig(
                                null, null, "https://wallet-api.urbanairship.com", null
                        )
                )
        );
    }
    @Test
    public void testDefaultUrl() {
        PassRequest request = PassRequest.newBuilder()
                                         .setAuth("test_user_name", "test_api_key")
                                         .setTemplateId("test_template_id")
                                         .build();

        assertEquals("https://wallet-api.urbanairship.com/v1/pass/test_template_id", request.getPassUrl().toString());
    }

    @Test
    public void testExecute() throws Exception {
        // Based off of example JSON in http://docs.urbanairship.com/api/wallet.html#create-pass
        final String requestJson = "{\n" +
                "    \"headers\":{\n" +
                "        \"expirationDate\":{\n" +
                "           \"value\":\"2014-08-20T9:41-08:00\"\n" +
                "         },\n" +
                "         \"barcodeAltText\":{\n" +
                "            \"value\":\"abc1234567890\",\n" +
                "            \"label\":\"label\"\n" +
                "         },\n" +
                "         \"barcode_value\":{\n" +
                "            \"value\":\"abc1234567890\",\n" +
                "            \"label\":\"label\"\n" +
                "         }\n" +
                "    },\n" +
                "    \"publicURL\" : {\n" +
                "        \"type\" : \"multiple\"\n" +
                "    },\n" +
                "    \"tag\": \"Text Tag\",\n" +
                "    \"externalId\": \"id123\",\n" +
                "    \"fields\":{\n" +
                "        \"Text\":{\n" +
                "           \"value\":\"Text Value\",\n" +
                "           \"label\":\"Text Label\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        String responseJson = "{\n" +
                "   \"createdAt\":\"2016-06-09T18:07:42Z\",\n" +
                "   \"publicUrl\":{\n" +
                "      \"path\":\"https:\\/\\/goo.gl\\/app\\/pay?link=https:\\/\\/www.android.com\\/payapp\\/savetoandroidpay\\/555\",\n" +
                "      \"type\":\"Single\"\n" +
                "   },\n" +
                "   \"id\":\"5\",\n" +
                "   \"status\":\"not_been_installed\"\n" +
                "}";


        requestSession.addResponse(HttpURLConnection.HTTP_OK, responseJson);

        Field field = Field.newBuilder()
                           .setName("Text")
                           .setValue("Text Value")
                           .setLabel("Text Label")
                           .build();

        PassRequest.Builder passRequestBuilder = PassRequest.newBuilder()
                                                            .setAuth("test_user_name", "test_api_key")
                                                            .setTemplateId("test_template_id")
                                                            .setExpirationDate("2014-08-20T9:41-08:00", null)
                                                            .setBarcodeAltText("abc1234567890", "label")
                                                            .setBarcodeValue("abc1234567890", "label")
                                                            .setTag("Text Tag")
                                                            .setExternalId("id123")
                                                            .addField(field);

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        Callback callback = new Callback() {
            @Override
            public void onResult(@NonNull Pass pass) {
                assertEquals("5", pass.getId());
                assertEquals(Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555"), pass.getPublicUri());
                latch.countDown();
            }

            @Override
            public void onError(int errorCode) {
                throw new RuntimeException();
            }
        };

        PassRequest passRequest = new PassRequest(passRequestBuilder, requestSession, executor);
        passRequest.execute(callback, null);
        latch.await();

        assertEquals(requestSession.getLastRequest().getBody(), new RequestBody.Json(JsonValue.parseString(requestJson)));
    }

    @Test
    public void testExecuteFail() throws Exception {
        requestSession.addResponse(HttpURLConnection.HTTP_BAD_REQUEST, null);
        PassRequest.Builder passRequestBuilder = PassRequest.newBuilder()
                                                            .setAuth("test_user_name", "test_api_key")
                                                            .setTemplateId("test_template_id");

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };

        PassRequest passRequest = new PassRequest(passRequestBuilder, requestSession, executor);

        final CountDownLatch latch = new CountDownLatch(1);
        Callback callback = new Callback() {
            @Override
            public void onResult(@NonNull Pass pass) {
                throw new RuntimeException();
            }

            @Override
            public void onError(int errorCode) {
                assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, errorCode);
                latch.countDown();
            }
        };

        passRequest.execute(callback, null);
        latch.await();
    }
}
