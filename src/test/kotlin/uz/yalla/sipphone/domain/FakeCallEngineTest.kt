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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FakeCallEngineTest {

    @Test
    fun `initial state is Idle`() {
        val engine = FakeCallEngine()
        assertIs<CallState.Idle>(engine.callState.value)
    }

    @Test
    fun `makeCall stores last number`() = runTest {
        val engine = FakeCallEngine()
        engine.makeCall("+998901234567")
        assertEquals("+998901234567", engine.lastCallNumber)
    }

    @Test
    fun `answerCall increments counter`() = runTest {
        val engine = FakeCallEngine()
        engine.simulateRinging("102", "Alex")
        engine.answerCall()
        assertEquals(1, engine.answerCallCount)
    }

    @Test
    fun `hangupCall increments counter`() = runTest {
        val engine = FakeCallEngine()
        engine.simulateActive()
        engine.hangupCall()
        assertEquals(1, engine.hangupCallCount)
    }

    @Test
    fun `toggleMute increments counter`() = runTest {
        val engine = FakeCallEngine()
        engine.simulateActive()
        engine.toggleMute()
        assertEquals(1, engine.toggleMuteCount)
    }

    @Test
    fun `toggleHold increments counter`() = runTest {
        val engine = FakeCallEngine()
        engine.simulateActive()
        engine.toggleHold()
        assertEquals(1, engine.toggleHoldCount)
    }

    @Test
    fun `simulateRinging sets Ringing state`() {
        val engine = FakeCallEngine()
        engine.simulateRinging("102", "Alex")
        val state = engine.callState.value
        assertIs<CallState.Ringing>(state)
        assertEquals("102", state.callerNumber)
        assertEquals("Alex", state.callerName)
    }

    @Test
    fun `simulateActive sets Active state`() {
        val engine = FakeCallEngine()
        engine.simulateActive(remoteNumber = "102", remoteName = "Alex", isOutbound = false)
        val state = engine.callState.value
        assertIs<CallState.Active>(state)
        assertEquals("102", state.remoteNumber)
        assertEquals(false, state.isOutbound)
    }

    @Test
    fun `simulateIdle resets to Idle`() {
        val engine = FakeCallEngine()
        engine.simulateActive()
        engine.simulateIdle()
        assertIs<CallState.Idle>(engine.callState.value)
    }

    @Test
    fun `makeCall returns success by default`() = runTest {
        val engine = FakeCallEngine()
        assertTrue(engine.makeCall("102").isSuccess)
    }

    @Test
    fun `makeCall returns configured failure`() = runTest {
        val engine = FakeCallEngine(
            makeCallResult = Result.failure(IllegalStateException("Not registered"))
        )
        val result = engine.makeCall("102")
        assertFalse(result.isSuccess)
    }
}
