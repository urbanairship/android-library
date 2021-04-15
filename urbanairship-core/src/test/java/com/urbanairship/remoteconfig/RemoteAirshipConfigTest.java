/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemoteAirshipConfigTest extends BaseTestCase {

    @Test
    public void testJson() {
        JsonValue json = JsonMap.newBuilder()
                                .putOpt("device_api_url", "https://deivce-api.examaple.com")
                                .putOpt("remote_data_url", "https://remote-data.examaple.com")
                                .putOpt("wallet_url", "https://wallet-api.examaple.com")
                                .putOpt("analytics_url", "https://analytics-api.examaple.com")
                                .putOpt("chat_url", "https://chat-api.examaple.com")
                                .putOpt("chat_socket_url", "wss://chat-api.examaple.com")
                                .build()
                                .toJsonValue();

        RemoteAirshipConfig airshipConfig = RemoteAirshipConfig.fromJson(json);
        assertEquals("https://deivce-api.examaple.com", airshipConfig.getDeviceApiUrl());
        assertEquals("https://remote-data.examaple.com", airshipConfig.getRemoteDataUrl());
        assertEquals("https://wallet-api.examaple.com", airshipConfig.getWalletUrl());
        assertEquals("https://analytics-api.examaple.com", airshipConfig.getAnalyticsUrl());
        assertEquals("https://chat-api.examaple.com", airshipConfig.getChatUrl());
        assertEquals("wss://chat-api.examaple.com", airshipConfig.getChatSocketUrl());

        assertEquals(json, airshipConfig.toJsonValue());
    }

    @Test
    public void testEmptyJson() {
        RemoteAirshipConfig airshipConfig = RemoteAirshipConfig.fromJson(JsonValue.NULL);
        assertNull(airshipConfig.getDeviceApiUrl());
        assertNull(airshipConfig.getRemoteDataUrl());
        assertNull(airshipConfig.getWalletUrl());
        assertNull(airshipConfig.getAnalyticsUrl());
        assertNull(airshipConfig.getChatSocketUrl());
        assertNull(airshipConfig.getChatUrl());

    }
}
