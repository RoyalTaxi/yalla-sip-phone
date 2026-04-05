package uz.yalla.sipphone.data.jcef

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BridgeAuditLogTest {
    private val log = BridgeAuditLog()

    @Test
    fun `logCommand masks phone number in params`() {
        val entry = log.formatEntry("makeCall", mapOf("number" to "+998901234567"))
        assertFalse(entry.contains("+998901234567"))
        assertTrue(entry.contains("***"))
    }

    @Test
    fun `logCommand does not mask non-phone params`() {
        val entry = log.formatEntry("setAgentStatus", mapOf("status" to "away"))
        assertTrue(entry.contains("away"))
    }
}
