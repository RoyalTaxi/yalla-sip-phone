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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FakeSipStackLifecycleTest {

    @Test
    fun `initialize sets flag and returns success`() = runTest {
        val lifecycle = FakeSipStackLifecycle()
        val result = lifecycle.initialize()
        assertTrue(result.isSuccess)
        assertTrue(lifecycle.initializeCalled)
    }

    @Test
    fun `shutdown sets flag`() = runTest {
        val lifecycle = FakeSipStackLifecycle()
        lifecycle.shutdown()
        assertTrue(lifecycle.shutdownCalled)
    }
}
