package com.urbanairship.http

public sealed class RequestAuth(
    public val isAuthTokenAuth: Boolean
) {
    public object BasicAppAuth : RequestAuth(false)
    public data class BasicAuth(val user: String, val password: String) : RequestAuth(false)
    public data class ChannelTokenAuth(val channelId: String) : RequestAuth(true)
    public data class ContactTokenAuth(val contactId: String) : RequestAuth(true)
    public data class BearerToken(val token: String) : RequestAuth(false)
}
