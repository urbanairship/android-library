/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PassTest extends BaseTestCase{

    @Test
    public void testParsePass() throws Exception {
        // This test doesn't require a full JSON payload
        String json = "{\n" +
                "   \"createdAt\":\"2016-06-09T18:07:42Z\",\n" +
                "   \"serialNumber\":\"5555555555555555555.55555_5555b5de-555e-555e-55a0-f5be5e5e55b5\",\n" +
                "   \"publicUrl\":{\n" +
                "      \"path\":\"https:\\/\\/goo.gl\\/app\\/pay?link=https:\\/\\/www.android.com\\/payapp\\/savetoandroidpay\\/555\",\n" +
                "      \"type\":\"Single\"\n" +
                "   },\n" +
                "   \"id\":\"5\",\n" +
                "   \"templateId\":\"49581\",\n" +
                "   \"url\":\"https:\\/\\/wallet-api.urbanairship.com\\/v1\\/pass\\/5\\/download\",\n" +
                "   \"updatedAt\":\"2016-06-09T18:07:42Z\",\n" +
                "   \"tags\":[\n" +
                "      \n" +
                "   ],\n" +
                "   \"status\":\"not_been_installed\"\n" +
                "}";

        Pass pass = Pass.parsePass(JsonValue.parseString(json));

        assertNotNull(pass);
        assertEquals(pass.getId(), "5");
        assertEquals(pass.getPublicUri(), Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555"));
    }

    @Test
    public void testRequestToSavePass() throws Exception {
        final Pass pass = new Pass(Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555"), "5");
        Context context = Mockito.mock(Context.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Intent intent = (Intent) invocation.getArguments()[0];
                assertEquals(pass.getPublicUri(), intent.getData());
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());

                return null;
            }
        }).when(context).startActivity(Mockito.any(Intent.class));

        pass.requestToSavePass(context);
    }

}
