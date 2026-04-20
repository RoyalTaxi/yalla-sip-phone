package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * In-window tooltip rendering.
 *
 * Tooltips render as a regular Compose overlay inside the main window — no `Popup`, no
 * native OS window, no mouse-ownership transfer, no focus flicker. Trade-off: the tooltip
 * can't draw above heavyweight AWT children (e.g., JCEF), which is why [YallaTooltip] picks
 * a placement (RIGHT / LEFT / TOP) that stays in pure-Compose areas.
 */
internal class TooltipHostState {
    /** Currently-shown tooltip content, or `null` if nothing is visible. */
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    /** Offset (in host coordinates) of the tooltip's top-left corner. */
    var offset: IntOffset by mutableStateOf(IntOffset.Zero)
        private set

    // Identity token of the anchor currently "owning" the host so a late hide from anchor A
    // can't clobber a show from anchor B that raced it.
    private var owner: Any? = null

    fun show(owner: Any, content: @Composable () -> Unit, offset: IntOffset) {
        this.owner = owner
        this.content = content
        this.offset = offset
    }

    fun hide(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            this.content = null
        }
    }
}

internal val LocalTooltipHost = staticCompositionLocalOf<TooltipHostState?> { null }

/**
 * Wraps app content and renders at-most-one tooltip as a regular Compose overlay.
 * Installed once at the root (by `YallaSipPhoneTheme`).
 */
@Composable
fun TooltipHost(content: @Composable () -> Unit) {
    val state = remember { TooltipHostState() }
    CompositionLocalProvider(LocalTooltipHost provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            state.content?.let { tooltipContent ->
                Box(modifier = Modifier.offset { state.offset }) {
                    tooltipContent()
                }
            }
        }
    }
}
