package uz.yalla.sipphone.data.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HoldControllerTest {

    @Test
    fun `request returns true on first call and invokes op`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        var invoked = 0

        val issued = controller.request { invoked++ }

        assertTrue(issued)
        assertEquals(1, invoked)
    }

    @Test
    fun `request returns false when already in progress and does not invoke op`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.request { }
        var invoked = 0

        val second = controller.request { invoked++ }

        assertFalse(second)
        assertEquals(0, invoked)
    }

    @Test
    fun `onMediaStateChanged clears in-progress and allows new request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.request { }

        controller.onMediaStateChanged()
        val second = controller.request { }

        assertTrue(second)
    }

    @Test
    fun `timeout clears in-progress automatically`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.request { }

        advanceTimeBy(1_100)
        advanceUntilIdle()
        val second = controller.request { }

        assertTrue(second)
    }

    @Test
    fun `cancel clears in-progress and timeout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)
        controller.request { }

        controller.cancel()
        val second = controller.request { }

        assertTrue(second)
    }

    @Test
    fun `op throwing clears in-progress and rethrows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = HoldController(CoroutineScope(dispatcher), timeoutMs = 1_000)

        assertFailsWith<RuntimeException> {
            controller.request { throw RuntimeException("boom") }
        }
        val second = controller.request { }

        assertTrue(second)
    }
}
