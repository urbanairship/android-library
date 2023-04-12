package com.urbanairship.http

public sealed class RequestAuth() {
    public object BasicAppAuth : RequestAuth()
    public data class BasicAuth(val user: String, val password: String) : RequestAuth()
    public data class ChannelTokenAuth(val channelId: String) : RequestAuth()
    public data class ContactTokenAuth(val contactId: String) : RequestAuth()
    public data class BearerToken(val token: String) : RequestAuth()
}
