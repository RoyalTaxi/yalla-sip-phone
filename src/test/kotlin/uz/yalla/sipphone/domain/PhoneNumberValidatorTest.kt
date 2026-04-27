package uz.yalla.sipphone.domain

import uz.yalla.sipphone.domain.agent.AgentStatus
import uz.yalla.sipphone.domain.call.CallEngine
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.call.CallerInfo
import uz.yalla.sipphone.domain.call.parseRemoteUri
import uz.yalla.sipphone.domain.sip.PhoneNumberValidator
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipAccountState
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.domain.sip.SipError
import uz.yalla.sipphone.domain.sip.SipConstants
import uz.yalla.sipphone.domain.sip.SipStackLifecycle

import kotlin.test.Test
import kotlin.test.assertTrue

class PhoneNumberValidatorTest {
    @Test
    fun `valid local number`() {
        assertTrue(PhoneNumberValidator.validate("101").isSuccess)
    }

    @Test
    fun `valid international number`() {
        assertTrue(PhoneNumberValidator.validate("+998901234567").isSuccess)
    }

    @Test
    fun `valid number with star and hash`() {
        assertTrue(PhoneNumberValidator.validate("*72#").isSuccess)
    }

    @Test
    fun `rejects empty string`() {
        assertTrue(PhoneNumberValidator.validate("").isFailure)
    }

    @Test
    fun `rejects letters`() {
        assertTrue(PhoneNumberValidator.validate("abc123").isFailure)
    }

    @Test
    fun `rejects control characters`() {
        assertTrue(PhoneNumberValidator.validate("123\r\n456").isFailure)
    }

    @Test
    fun `rejects too long number`() {
        assertTrue(PhoneNumberValidator.validate("+1234567890123456789012").isFailure)
    }

    @Test
    fun `sanitized number is trimmed`() {
        val result = PhoneNumberValidator.validate("  +998901234567  ")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() == "+998901234567")
    }
}
