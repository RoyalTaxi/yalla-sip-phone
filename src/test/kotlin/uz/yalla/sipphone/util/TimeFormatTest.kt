package uz.yalla.sipphone.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {

    @Test
    fun `zero seconds`() {
        assertEquals("00:00", formatDuration(0))
    }

    @Test
    fun `59 seconds`() {
        assertEquals("00:59", formatDuration(59))
    }

    @Test
    fun `60 seconds shows 01 00`() {
        assertEquals("01:00", formatDuration(60))
    }

    @Test
    fun `90 seconds shows 01 30`() {
        assertEquals("01:30", formatDuration(90))
    }

    @Test
    fun `3661 seconds shows 61 01`() {
        assertEquals("61:01", formatDuration(3661))
    }
}
