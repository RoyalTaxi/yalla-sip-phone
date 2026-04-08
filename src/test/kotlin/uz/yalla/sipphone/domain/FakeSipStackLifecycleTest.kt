package uz.yalla.sipphone.domain

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
