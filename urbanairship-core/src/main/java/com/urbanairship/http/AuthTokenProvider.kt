package com.urbanairship.http

public interface AuthTokenProvider {
    public fun fetchToken(identifier: String): String
    public fun expireToken(token: String)
}
