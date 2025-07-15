/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import com.urbanairship.AirshipExecutors
import com.urbanairship.Autopilot
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.activity.ThemedActivity
import com.urbanairship.http.Request
import com.urbanairship.http.RequestException
import com.urbanairship.http.ResponseParser
import com.urbanairship.util.UAHttpStatusUtil

public class WalletLoadingActivity public constructor() : ThemedActivity() {

    private val liveData = MutableLiveData<Result>()
    private val executor = AirshipExecutors.threadPoolExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ua_activity_wallet_loading)

        Autopilot.automaticTakeOff(application)

        val url = intent.data ?: run {
            UALog.w("No data found, unable to process link.")
            finish()
            return
        }

        liveData.observe(this) { result: Result ->
            if (result.exception != null || result.uri == null) {
                finish()
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, result.uri)
                startActivity(browserIntent)
            }
        }

        resolveWalletUrl(url)
    }

    private fun resolveWalletUrl(url: Uri) {
        executor.submit {
            try {
                UALog.d("Runner starting")

                val session = UAirship.shared().runtimeConfig.requestSession

                val request = Request(url, "GET", false)

                val response = session.execute(
                    request = request,
                    parser = ResponseParser { status: Int, responseHeaders: Map<String, String>, _: String? ->
                        if (UAHttpStatusUtil.inRedirectionRange(status)) {
                            return@ResponseParser responseHeaders["Location"]
                        }

                        null
                    })

                if (response.result != null) {
                    liveData.postValue(
                        Result(Uri.parse(response.result), null)
                    )
                } else {
                    UALog.w("No result found for Wallet URL, finishing action.")
                    liveData.postValue(
                        Result(null, null)
                    )
                }
            } catch (e: RequestException) {
                liveData.postValue(Result(null, e))
            }
        }
    }

    private class Result(var uri: Uri?, var exception: Exception?)
}
