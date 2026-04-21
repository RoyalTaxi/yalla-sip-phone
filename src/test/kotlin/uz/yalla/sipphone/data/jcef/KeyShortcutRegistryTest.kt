package uz.yalla.sipphone.data.jcef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.awt.Component
import java.awt.event.KeyEvent

/**
 * Unit tests for [KeyShortcutRegistry] / [NormalizedKey]. The regex of interest is the
 * key-name table in `keyName(code, location)` — previously missing numpad keys, which meant
 * the frontend registering `"numpadmultiply"` would never see a `keyPressed` event when the
 * operator hit the numpad `*`.
 */
class KeyShortcutRegistryTest {

    private fun event(
        code: Int,
        modifiers: Int = 0,
        location: Int = KeyEvent.KEY_LOCATION_STANDARD,
    ): KeyEvent = KeyEvent(
        DummyComponent,
        KeyEvent.KEY_PRESSED,
        /* when */ 0L,
        modifiers,
        code,
        KeyEvent.CHAR_UNDEFINED,
        location,
    )

    @Test
    fun `parse accepts web-standard numpad names regardless of case or modifier order`() {
        val a = NormalizedKey.parse("NumpadMultiply")
        val b = NormalizedKey.parse("ctrl+numpad5")
        val c = NormalizedKey.parse("numpadenter+shift")
        assertEquals(NormalizedKey(ctrl = false, shift = false, alt = false, meta = false, key = "numpadmultiply"), a)
        assertEquals(NormalizedKey(ctrl = true, shift = false, alt = false, meta = false, key = "numpad5"), b)
        assertEquals(NormalizedKey(ctrl = false, shift = true, alt = false, meta = false, key = "numpadenter"), c)
    }

    @Test
    fun `numpad multiply key produces numpadmultiply — regression for frontend bug`() {
        val ev = event(KeyEvent.VK_MULTIPLY, location = KeyEvent.KEY_LOCATION_NUMPAD)
        val key = NormalizedKey.fromEvent(ev)
        assertNotNull(key)
        assertEquals("numpadmultiply", key!!.key)
    }

    @Test
    fun `numpad 0-9 distinguished from top-row digits via key location`() {
        val topRowFive = event(KeyEvent.VK_5, location = KeyEvent.KEY_LOCATION_STANDARD)
        val numpadFive = event(KeyEvent.VK_5, location = KeyEvent.KEY_LOCATION_NUMPAD)
        assertEquals("5", NormalizedKey.fromEvent(topRowFive)?.key)
        assertEquals("numpad5", NormalizedKey.fromEvent(numpadFive)?.key)
    }

    @Test
    fun `numpad Enter distinguished from regular Enter`() {
        val regular = event(KeyEvent.VK_ENTER)
        val numpad = event(KeyEvent.VK_ENTER, location = KeyEvent.KEY_LOCATION_NUMPAD)
        assertEquals("enter", NormalizedKey.fromEvent(regular)?.key)
        assertEquals("numpadenter", NormalizedKey.fromEvent(numpad)?.key)
    }

    @Test
    fun `dedicated numpad arithmetic VKs map to numpad-prefixed names`() {
        assertEquals("numpadadd", NormalizedKey.fromEvent(event(KeyEvent.VK_ADD))?.key)
        assertEquals("numpadsubtract", NormalizedKey.fromEvent(event(KeyEvent.VK_SUBTRACT))?.key)
        assertEquals("numpaddivide", NormalizedKey.fromEvent(event(KeyEvent.VK_DIVIDE))?.key)
        assertEquals("numpaddecimal", NormalizedKey.fromEvent(event(KeyEvent.VK_DECIMAL))?.key)
    }

    @Test
    fun `end-to-end — frontend registers numpadmultiply, press produces a match`() {
        val registry = KeyShortcutRegistry()
        val added = registry.register(listOf("space", "numpadmultiply"))
        assertEquals(2, added)
        assertEquals(
            "space",
            registry.match(event(KeyEvent.VK_SPACE)),
        )
        assertEquals(
            "numpadmultiply",
            registry.match(event(KeyEvent.VK_MULTIPLY, location = KeyEvent.KEY_LOCATION_NUMPAD)),
        )
    }

    @Test
    fun `registration round-trips the exact frontend-provided string`() {
        val registry = KeyShortcutRegistry()
        registry.register(listOf("Ctrl+NumpadMultiply"))
        // When ctrl+numpad* is pressed, we return the exact string the frontend sent,
        // not a canonicalized form. That way the frontend switch(e.key) matches regardless.
        val matched = registry.match(
            event(
                KeyEvent.VK_MULTIPLY,
                modifiers = KeyEvent.CTRL_DOWN_MASK,
                location = KeyEvent.KEY_LOCATION_NUMPAD,
            ),
        )
        assertEquals("Ctrl+NumpadMultiply", matched)
    }

    @Test
    fun `unmapped key codes return null — no phantom matches`() {
        // VK_CAPS_LOCK etc. aren't in the key-name table; registry returns null.
        val result = NormalizedKey.fromEvent(event(KeyEvent.VK_CAPS_LOCK))
        assertNull(result)
    }

    @Test
    fun `clear empties the registry`() {
        val registry = KeyShortcutRegistry()
        registry.register(listOf("space"))
        registry.clear()
        assertNull(registry.match(event(KeyEvent.VK_SPACE)))
    }

    private object DummyComponent : Component()
}
