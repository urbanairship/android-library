package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class StringExtensionsTest {
    @Test
    public fun testEmailValidationValidEmails() {
        val validEmails = listOf(
            "simple@example.com",
            "very.common@example.com",
            "disposable.style.email.with+symbol@example.com",
            "other.email-with-hyphen@example.com",
            "fully-qualified-domain@example.com",
            "user.name+tag+sorting@example.com",
            "x@y.z",
            "user123@domain.com",
            "user.name@domain.com",
            "a@domain.com",
            "user@sub.domain.com",
            "user-name@domain.com",
            "user.@domain.com",
            ".user@domain.com",
            "user@.domain.com",
            "user..name@domain.com",
            "user+name@domain.com",
            "user!#$%&'*+-/=?^_`{|}~@domain.com",
            "long.email-address-with-hyphens@and.subdomains.example.com"
        )

        validEmails.forEach {
            assertThat(it.airshipIsValidEmail()).isTrue()
        }
    }

    @Test
    public fun testEmailValidationInvalidEmails() {
        val validEmails = listOf(
            "",
            "  ",
            "@",
            "@domain.com",
            "user@",
            "user",
            "domain.com",
            "user@domain",
            "@domain",
            "user@@domain.com",
            "user@domain@test.com",
            "user @domain.com",
            "user@ domain.com",
            "us er@domain.com",
            "user@do main.com",
            " user@domain.com",
            "user@domain.com "
        )


        validEmails.forEach {
            assertThat(it.airshipIsValidEmail()).isFalse()
        }
    }
}
