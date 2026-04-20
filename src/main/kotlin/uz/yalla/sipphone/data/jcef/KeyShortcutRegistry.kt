package uz.yalla.sipphone.data.jcef

import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Frontend-driven keyboard shortcut registry.
 *
 * Rationale: the dispatcher web UI evolves independently of the native shell. Hardcoding
 * shortcuts in Kotlin (Ctrl+Enter to answer, Ctrl+M to mute, …) coupled the two and made the
 * web team ship-blocked on native releases for every new hotkey. Also — because JCEF captures
 * keyboard events when the webview has focus — the old shortcuts didn't even fire reliably.
 *
 * New flow:
 *   1. Web calls `window.YallaSIP.registerKeyListeners(["ctrl+enter","ctrl+m","space"])`.
 *   2. Native stores the normalized combos here.
 *   3. On every key press (AWT-level OR pre-dispatched from JCEF via CefKeyboardHandler),
 *      native checks the registry. On match: emit `keyPressed` bridge event with the original
 *      combo string, consume the event so it isn't processed twice.
 *   4. Web listens for `keyPressed` and runs its own handler.
 *
 * Note the built-in shortcuts `Ctrl+Shift+Alt+B` (update channel toggle) and
 * `Ctrl+Shift+Alt+D` (diagnostics) remain native-only and aren't reachable through this
 * registry — they're debug surfaces, not something the frontend should override.
 */
class KeyShortcutRegistry {
    // Key: normalized combo; value: the exact original string the frontend registered, so
    // the bridge event payload round-trips what the caller sent.
    private val registered = ConcurrentHashMap<NormalizedKey, String>()

    /** Replace the registry with the supplied list. Unknown/malformed combos are skipped. */
    fun register(keys: List<String>): Int {
        registered.clear()
        var added = 0
        for (raw in keys) {
            val norm = NormalizedKey.parse(raw) ?: continue
            registered[norm] = raw
            added++
        }
        return added
    }

    /** Clears everything — used on bridge handshake reset so registrations don't leak
     *  across page reloads / logouts. */
    fun clear() {
        registered.clear()
    }

    /**
     * If the incoming AWT event matches a registered shortcut, return the exact original
     * string it was registered with. Otherwise null.
     */
    fun match(event: KeyEvent): String? {
        val pressed = NormalizedKey.fromEvent(event) ?: return null
        return registered[pressed]
    }
}

/**
 * Modifier-agnostic, case-insensitive key combo representation. Modifier flags are explicit
 * so order of "ctrl+shift" vs "shift+ctrl" in the frontend string doesn't matter.
 */
data class NormalizedKey(
    val ctrl: Boolean,
    val shift: Boolean,
    val alt: Boolean,
    val meta: Boolean,
    val key: String,
) {
    companion object {
        fun parse(raw: String): NormalizedKey? {
            val parts = raw.lowercase().split("+").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            var ctrl = false; var shift = false; var alt = false; var meta = false
            var key: String? = null
            for (p in parts) {
                when (p) {
                    "ctrl", "control" -> ctrl = true
                    "shift" -> shift = true
                    "alt", "option" -> alt = true
                    "meta", "cmd", "command", "super" -> meta = true
                    else -> key = p
                }
            }
            return key?.let { NormalizedKey(ctrl, shift, alt, meta, it) }
        }

        fun fromEvent(event: KeyEvent): NormalizedKey? {
            val keyStr = keyName(event.keyCode) ?: return null
            return NormalizedKey(
                ctrl = event.isControlDown,
                shift = event.isShiftDown,
                alt = event.isAltDown,
                meta = event.isMetaDown,
                key = keyStr,
            )
        }

        private fun keyName(code: Int): String? = when (code) {
            KeyEvent.VK_ENTER -> "enter"
            KeyEvent.VK_SPACE -> "space"
            KeyEvent.VK_ESCAPE -> "escape"
            KeyEvent.VK_TAB -> "tab"
            KeyEvent.VK_BACK_SPACE -> "backspace"
            KeyEvent.VK_DELETE -> "delete"
            KeyEvent.VK_LEFT -> "left"
            KeyEvent.VK_RIGHT -> "right"
            KeyEvent.VK_UP -> "up"
            KeyEvent.VK_DOWN -> "down"
            KeyEvent.VK_HOME -> "home"
            KeyEvent.VK_END -> "end"
            KeyEvent.VK_PAGE_UP -> "pageup"
            KeyEvent.VK_PAGE_DOWN -> "pagedown"
            in KeyEvent.VK_0..KeyEvent.VK_9 -> ('0' + (code - KeyEvent.VK_0)).toString()
            in KeyEvent.VK_A..KeyEvent.VK_Z -> ('a' + (code - KeyEvent.VK_A)).toString()
            in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "f${code - KeyEvent.VK_F1 + 1}"
            KeyEvent.VK_MINUS -> "-"
            KeyEvent.VK_EQUALS -> "="
            KeyEvent.VK_COMMA -> ","
            KeyEvent.VK_PERIOD -> "."
            KeyEvent.VK_SLASH -> "/"
            KeyEvent.VK_SEMICOLON -> ";"
            KeyEvent.VK_QUOTE -> "'"
            KeyEvent.VK_OPEN_BRACKET -> "["
            KeyEvent.VK_CLOSE_BRACKET -> "]"
            KeyEvent.VK_BACK_SLASH -> "\\"
            else -> null
        }
    }
}
