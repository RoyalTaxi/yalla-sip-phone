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

class AgentStatusTest {
    @Test
    fun `all expected statuses exist`() {
        val expected = setOf("READY", "AWAY", "BREAK", "WRAP_UP", "OFFLINE")
        val actual = AgentStatus.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
