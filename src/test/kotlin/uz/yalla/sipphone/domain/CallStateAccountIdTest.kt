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
import kotlin.test.assertEquals

class CallStateAccountIdTest {
    @Test
    fun `Ringing carries accountId`() {
        val state = CallState.Ringing("c1", "+998901234567", null, false, "1001@sip.yalla.uz")
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `Active carries accountId`() {
        val state = CallState.Active("c1", "+998901234567", null, false, false, false, "1001@sip.yalla.uz")
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `Ending carries accountId`() {
        val state = CallState.Ending("c1", "1001@sip.yalla.uz")
        assertEquals("1001@sip.yalla.uz", state.accountId)
    }

    @Test
    fun `accountId defaults to empty string`() {
        val state = CallState.Ringing("c1", "+998901234567", null, false)
        assertEquals("", state.accountId)
    }
}
