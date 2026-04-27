package uz.yalla.sipphone.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNumberMaskerTest {
    @Test
    fun `masks long number showing last 2 digits`() {
        val masked = PhoneNumberMasker.mask("+998901234567")
        assertTrue(masked.endsWith("67"))
        assertTrue(masked.contains("*"))
    }

    @Test
    fun `masks short number`() {
        val masked = PhoneNumberMasker.mask("101")
        assertEquals(3, masked.length)
        assertTrue(masked.endsWith("01"))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", PhoneNumberMasker.mask(""))
    }

    @Test
    fun `two char number masks first`() {
        val masked = PhoneNumberMasker.mask("42")
        assertEquals("*2", masked)
    }

    @Test
    fun `maskParams masks recognised phone keys`() {
        val masked = PhoneNumberMasker.maskParams(mapOf("number" to "+998901234567"))
        assertTrue(masked["number"]!!.endsWith("67"))
        assertTrue(masked["number"]!!.contains("*"))
    }

    @Test
    fun `maskParams leaves non-phone keys untouched`() {
        val masked = PhoneNumberMasker.maskParams(mapOf("status" to "away"))
        assertEquals("away", masked["status"])
    }
}
