package uz.yalla.sipphone.data.jcef.keys

import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap

class KeyShortcutRegistry {

    private val registered = ConcurrentHashMap<NormalizedKey, String>()

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

    fun clear() {
        registered.clear()
    }

    fun match(event: KeyEvent): String? {
        val pressed = NormalizedKey.fromEvent(event) ?: return null
        return registered[pressed]
    }
}

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
            val keyStr = keyName(event.keyCode, event.keyLocation) ?: return null
            return NormalizedKey(
                ctrl = event.isControlDown,
                shift = event.isShiftDown,
                alt = event.isAltDown,
                meta = event.isMetaDown,
                key = keyStr,
            )
        }

        private fun keyName(code: Int, location: Int): String? {
            val onNumpad = location == KeyEvent.KEY_LOCATION_NUMPAD
            return when (code) {
                KeyEvent.VK_ENTER -> if (onNumpad) "numpadenter" else "enter"
                KeyEvent.VK_SPACE -> "space"
                KeyEvent.VK_ESCAPE -> "escape"
                KeyEvent.VK_TAB -> "tab"
                KeyEvent.VK_BACK_SPACE -> "backspace"
                KeyEvent.VK_DELETE -> "delete"
                KeyEvent.VK_INSERT -> "insert"
                KeyEvent.VK_LEFT -> "left"
                KeyEvent.VK_RIGHT -> "right"
                KeyEvent.VK_UP -> "up"
                KeyEvent.VK_DOWN -> "down"
                KeyEvent.VK_HOME -> "home"
                KeyEvent.VK_END -> "end"
                KeyEvent.VK_PAGE_UP -> "pageup"
                KeyEvent.VK_PAGE_DOWN -> "pagedown"
                in KeyEvent.VK_0..KeyEvent.VK_9 ->
                    if (onNumpad) "numpad${code - KeyEvent.VK_0}"
                    else ('0' + (code - KeyEvent.VK_0)).toString()
                in KeyEvent.VK_A..KeyEvent.VK_Z -> ('a' + (code - KeyEvent.VK_A)).toString()
                in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "f${code - KeyEvent.VK_F1 + 1}"

                in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 -> "numpad${code - KeyEvent.VK_NUMPAD0}"
                KeyEvent.VK_MULTIPLY -> "numpadmultiply"
                KeyEvent.VK_ADD -> "numpadadd"
                KeyEvent.VK_SUBTRACT -> "numpadsubtract"
                KeyEvent.VK_DIVIDE -> "numpaddivide"
                KeyEvent.VK_DECIMAL -> "numpaddecimal"
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
}
