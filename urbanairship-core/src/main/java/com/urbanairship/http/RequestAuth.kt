package com.urbanairship.http

public sealed class RequestAuth() {

    // / App key:secret basic auth
    public object BasicAppAuth : RequestAuth()

    // / Basic auth with provided key:secret
    public data class BasicAuth(val user: String, val password: String) : RequestAuth()

    // / Client generated app token - app secret signed token of key, nonce, timestamp
    public object GeneratedAppToken : RequestAuth()

    // / Client generated app token - app secret signed token of key, channel, nonce, timestamp
    public data class GeneratedChannelToken(val channelId: String) : RequestAuth()

    // / Channel auth token
    public data class ChannelTokenAuth(val channelId: String) : RequestAuth()

    // / Contact auth token
    public data class ContactTokenAuth(val contactId: String) : RequestAuth()

    // / Bearer token
    public data class BearerToken(val token: String) : RequestAuth()
}
