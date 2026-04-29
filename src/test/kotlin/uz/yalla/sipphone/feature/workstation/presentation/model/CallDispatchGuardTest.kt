@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package uz.yalla.sipphone.feature.workstation.presentation.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.domain.FakeCallEngine
import uz.yalla.sipphone.domain.call.CallState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors the dispatch-guard logic in [WorkstationComponent.dispatchCall]:
 *
 * ```
 * if (callEngine.callState.value !is CallState.Idle) return
 * if (!callDispatching.compareAndSet(false, true)) return
 * try { callEngine.makeCall(number) } finally { callDispatching.set(false) }
 * ```
 *
 * The contract: among N concurrent dispatch attempts triggered before the first call's
 * state has transitioned out of Idle, only ONE reaches `makeCall`. Without the
 * AtomicBoolean gate, a fast double-tap on the toolbar Call button could enqueue two
 * outbound calls before the first one's state transition landed (the JCEF JS bridge
 * already guards on `callState != Idle` but the toolbar did not).
 */
class CallDispatchGuardTest {

    @Test
    fun `gate admits only one of N concurrent dispatchers`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeCallEngine()
        engine.makeCallGate = CompletableDeferred()
        val gate = AtomicBoolean(false)
        val firstStarted = CompletableDeferred<Unit>()

        // First dispatcher wins the gate, hits makeCall (which suspends on the gate),
        // and signals via firstStarted before suspending.
        val first = launch {
            if (engine.callState.value !is CallState.Idle) return@launch
            if (!gate.compareAndSet(false, true)) return@launch
            try {
                firstStarted.complete(Unit)
                engine.makeCall("100")
            } finally {
                gate.set(false)
            }
        }
        firstStarted.await()

        // Five more attempts arrive while the first is still in flight. Each MUST be
        // rejected — the AtomicBoolean is held by the first dispatcher.
        val others = (0 until 5).map {
            launch {
                if (engine.callState.value !is CallState.Idle) return@launch
                if (!gate.compareAndSet(false, true)) return@launch
                try {
                    engine.makeCall("100")
                } finally {
                    gate.set(false)
                }
            }
        }
        others.forEach { it.join() }

        assertEquals(
            1,
            engine.makeCallCount,
            "gate must admit only one concurrent dispatcher; saw ${engine.makeCallCount}",
        )

        // Release the first dispatcher and clean up.
        engine.makeCallGate!!.complete(Unit)
        first.join()
    }

    @Test
    fun `non-Idle state rejects all dispatchers without entering the gate`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeCallEngine()
        engine.simulateActive(remoteNumber = "100")
        val gate = AtomicBoolean(false)

        repeat(3) {
            launch {
                if (engine.callState.value !is CallState.Idle) return@launch
                if (!gate.compareAndSet(false, true)) return@launch
                try {
                    engine.makeCall("100")
                } finally {
                    gate.set(false)
                }
            }.join()
        }

        assertEquals(
            0,
            engine.makeCallCount,
            "dispatchers must not call makeCall when callState is not Idle",
        )
    }

    @Test
    fun `gate releases on completion so subsequent dispatch can proceed`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeCallEngine()
        val gate = AtomicBoolean(false)

        suspend fun tryDispatch() {
            if (engine.callState.value !is CallState.Idle) return
            if (!gate.compareAndSet(false, true)) return
            try {
                engine.makeCall("100")
            } finally {
                gate.set(false)
            }
        }

        tryDispatch()
        tryDispatch()
        tryDispatch()

        assertEquals(
            3,
            engine.makeCallCount,
            "sequential dispatches (after each completes) must each pass the gate",
        )
    }
}
