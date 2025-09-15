/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PassTest {

    @Test
    public fun testParsePass() {
        // This test doesn't require a full JSON payload
        val json = """{
           "createdAt":"2016-06-09T18:07:42Z",
           "serialNumber":"5555555555555555555.55555_5555b5de-555e-555e-55a0-f5be5e5e55b5",
           "publicUrl":{
              "path":"https:\/\/goo.gl\/app\/pay?link=https:\/\/www.android.com\/payapp\/savetoandroidpay\/555",
              "type":"Single"
           },
           "id":"5",
           "templateId":"49581",
           "url":"https:\/\/wallet-api.urbanairship.com\/v1\/pass\/5\/download",
           "updatedAt":"2016-06-09T18:07:42Z",
           "tags":[

           ],
           "status":"not_been_installed"
        }""".trimIndent()

        val pass = Pass.parsePass(JsonValue.parseString(json))

        Assert.assertNotNull(pass)
        Assert.assertEquals(pass?.id, "5")
        Assert.assertEquals(
            pass?.publicUri,
            Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555")
        )
    }

    @Test
    public fun testRequestToSavePass() {
        val pass = Pass(
            Uri.parse("https://goo.gl/app/pay?link=https://www.android.com/payapp/savetoandroidpay/555"),
            "5"
        )
        val context: Context = mockk() {
            every { startActivity(any()) } answers {
                val intent = firstArg<Intent>()
                Assert.assertEquals(pass.publicUri, intent.data)
                Assert.assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK.toLong(), intent.flags.toLong())
            }
        }

        pass.requestToSavePass(context)
    }
}
