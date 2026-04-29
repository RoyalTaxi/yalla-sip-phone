@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package uz.yalla.sipphone.feature.workstation.sideeffect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.domain.call.CallState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the distinct-on-phase behavior of [CallSideEffects.start]. The audit flagged
 * that re-emitting `CallState.Active` (e.g. on every mute or hold toggle) was repeatedly
 * calling `ringtone.stop()` — harmless but spammy and a performance pessimization.
 *
 * Uses [UnconfinedTestDispatcher] so collection runs eagerly and we don't have to manually
 * advance the scheduler.
 */
class CallSideEffectsTest {

    @Test
    fun `incoming Ringing fires play once`() = runTest {
        val ringtone = CountingRingtonePlayer()
        val callState = MutableStateFlow<CallState>(CallState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        CallSideEffects(ringtone).start(scope, callState)

        // Initial Idle emission fires stop (harmless; clip=null, just establishes state).
        val baselineStops = ringtone.stopCount
        callState.value = ringingInbound()
        scope.cancel()

        assertEquals(1, ringtone.playCount)
        assertEquals(baselineStops, ringtone.stopCount, "Idle->Ringing must not call stop")
    }

    @Test
    fun `outbound Ringing does NOT fire play`() = runTest {
        val ringtone = CountingRingtonePlayer()
        val callState = MutableStateFlow<CallState>(CallState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        CallSideEffects(ringtone).start(scope, callState)

        callState.value = ringingOutbound()
        scope.cancel()

        assertEquals(0, ringtone.playCount)
    }

    @Test
    fun `Active mute and hold toggles do not re-fire stop`() = runTest {
        val ringtone = CountingRingtonePlayer()
        val callState = MutableStateFlow<CallState>(CallState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        CallSideEffects(ringtone).start(scope, callState)

        callState.value = ringingInbound()
        assertEquals(1, ringtone.playCount)
        val baselineStopsBeforeActive = ringtone.stopCount

        // Caller answers — transitions to Active. Phase changes from Ringing-incoming to
        // not-Ringing-incoming, so ringtone.stop fires once.
        callState.value = active(isMuted = false, isOnHold = false)
        val stopsAfterAnswer = ringtone.stopCount
        assertEquals(baselineStopsBeforeActive + 1, stopsAfterAnswer)

        // Operator toggles mute and hold — each emits a NEW Active instance with the same
        // "not incoming-ringing" classification. Without distinctUntilChanged, this would
        // call stop() three more times.
        callState.value = active(isMuted = true, isOnHold = false)
        callState.value = active(isMuted = false, isOnHold = false)
        callState.value = active(isMuted = true, isOnHold = true)
        scope.cancel()

        assertEquals(stopsAfterAnswer, ringtone.stopCount, "mute/hold toggles must not re-fire ringtone.stop")
        assertEquals(1, ringtone.playCount, "mute/hold toggles must not fire ringtone.play")
    }

    @Test
    fun `back-to-back inbound rings each fire play once`() = runTest {
        val ringtone = CountingRingtonePlayer()
        val callState = MutableStateFlow<CallState>(CallState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        CallSideEffects(ringtone).start(scope, callState)

        callState.value = ringingInbound("100")
        callState.value = CallState.Idle
        callState.value = ringingInbound("101")
        scope.cancel()

        assertEquals(2, ringtone.playCount, "each distinct Ringing-incoming phase plays once")
    }

    private fun ringingInbound(number: String = "100") =
        CallState.Ringing(callId = "c", callerNumber = number, callerName = null, isOutbound = false)

    private fun ringingOutbound() =
        CallState.Ringing(callId = "c", callerNumber = "100", callerName = null, isOutbound = true)

    private fun active(isMuted: Boolean, isOnHold: Boolean) = CallState.Active(
        callId = "c",
        remoteNumber = "100",
        remoteName = null,
        isOutbound = false,
        isMuted = isMuted,
        isOnHold = isOnHold,
    )

    private class CountingRingtonePlayer : RingtonePlayer() {
        var playCount = 0; private set
        var stopCount = 0; private set

        override fun play() { playCount++ }
        override fun stop() { stopCount++ }
        override fun release() { /* no-op for tests */ }
    }
}
