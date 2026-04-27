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

internal class TooltipHostState {

    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    var offset: IntOffset by mutableStateOf(IntOffset.Zero)
        private set

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
