/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

/**
 * Model object for User credentials.
 *
 * @hide
 */
internal class UserCredentials(
    val username: String,
    val password: String
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserCredentials

        if (username != other.username) return false
        return password == other.password
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }
}
