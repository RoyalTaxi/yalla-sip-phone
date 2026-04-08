package uz.yalla.sipphone.domain

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
