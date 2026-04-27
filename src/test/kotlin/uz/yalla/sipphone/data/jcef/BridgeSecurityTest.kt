package uz.yalla.sipphone.data.jcef

import uz.yalla.sipphone.data.jcef.bridge.BridgeSecurity

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BridgeSecurityTest {
    private val security = BridgeSecurity()

    @Test
    fun `allows first command`() {
        assertTrue(security.checkRateLimit("makeCall"))
    }

    @Test
    fun `blocks after exceeding limit`() {
        repeat(5) { security.checkRateLimit("makeCall") }
        assertFalse(security.checkRateLimit("makeCall"))
    }

    @Test
    fun `different commands have separate limits`() {
        repeat(5) { security.checkRateLimit("makeCall") }
        assertTrue(security.checkRateLimit("getState"))
    }

    @Test
    fun `getState has higher limit`() {
        repeat(59) { assertTrue(security.checkRateLimit("getState")) }
        assertTrue(security.checkRateLimit("getState"))
    }
}
