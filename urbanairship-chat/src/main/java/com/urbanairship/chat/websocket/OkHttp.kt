package com.urbanairship.chat.websocket

import android.net.TrafficStats
import androidx.annotation.RestrictTo
import com.urbanairship.chat.BuildConfig
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.OkHttpClient

/**
 * Singleton providing a lazily instantiated `OkHttpClient`.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OkHttp {

    internal val sharedClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .apply {
                // Work around for OkHttp's issues with untagged sockets under StrictMode
                // see: https://github.com/square/okhttp/issues/3537
                if (BuildConfig.DEBUG) { socketFactory(strictModeWorkaroundSocketFactory) }
            }
            .build()
    }

    private val strictModeWorkaroundSocketFactory by lazy {
        object : SocketFactory() {
            private val defaultFactory = getDefault()

            override fun createSocket() = ensureTagged { createSocket() }

            override fun createSocket(host: String?, port: Int) =
                ensureTagged { createSocket(host, port) }

            override fun createSocket(
                host: String?,
                port: Int,
                localHost: InetAddress?,
                localPort: Int
            ) = ensureTagged { createSocket(host, port, localHost, localPort) }

            override fun createSocket(host: InetAddress?, port: Int) =
                ensureTagged { createSocket(host, port) }

            override fun createSocket(
                address: InetAddress?,
                port: Int,
                localAddress: InetAddress?,
                localPort: Int
            ) = ensureTagged { createSocket(address, port, localAddress, localPort) }

            private fun ensureTagged(block: SocketFactory.() -> Socket): Socket {
                if (TrafficStats.getThreadStatsTag() == -1) {
                    TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
                }
                return block.invoke(defaultFactory)
            }
        }
    }
}
