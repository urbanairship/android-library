package com.urbanairship.android.layout.environment

import com.urbanairship.TestClock
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import java.util.Date
import java.util.UUID
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasChannelRegistrarTest {

    private val mockEmailRegistration: (String, EmailRegistrationOptions) -> Unit = mockk(relaxed = true)
    private val testClock: TestClock = TestClock()
    private val registrar: ThomasChannelRegistrar = ThomasChannelRegistrar(clock = testClock,  registerEmail = mockEmailRegistration)

    private val properties: JsonMap = jsonMapOf(
        UUID.randomUUID().toString() to UUID.randomUUID().toString()
    )
    private val address: String = UUID.randomUUID().toString()

    @Test
    public fun testRegisterEmailCommercially() {
        val options = ThomasEmailRegistrationOptions.Commercial(
            optedIn = true,
            properties = properties
        )

        val expectedOptions = EmailRegistrationOptions.commercialOptions(
            transactionalOptedIn = null,
            commercialOptedIn = Date(testClock.currentTimeMillis),
            properties = properties
        )

        registrar.register(
            listOf(
                ThomasChannelRegistration.Email(address, options)
            )
        )

        verify {
            mockEmailRegistration(address, expectedOptions)
        }
    }

    @Test
    public fun testRegisterEmailTransactionally() {
        val options = ThomasEmailRegistrationOptions.Transactional(
            properties = properties
        )

        val expectedOptions = EmailRegistrationOptions.options(
            transactionalOptedIn = null,
            properties = properties,
            doubleOptIn = false
        )

        registrar.register(
            listOf(
                ThomasChannelRegistration.Email(address, options)
            )
        )

        verify {
            mockEmailRegistration(address, expectedOptions)
        }
    }

    @Test
    public fun testRegisterEmailDoubleOptIn() {
        val options = ThomasEmailRegistrationOptions.DoubleOptIn(
            properties = properties
        )

        val expectedOptions = EmailRegistrationOptions.options(
            properties = properties,
            doubleOptIn = true
        )

        registrar.register(
            listOf(
                ThomasChannelRegistration.Email(address, options)
            )
        )

        verify {
            mockEmailRegistration(address, expectedOptions)
        }
    }


}
