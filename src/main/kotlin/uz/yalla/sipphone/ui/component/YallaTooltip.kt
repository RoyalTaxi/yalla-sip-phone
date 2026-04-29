package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun YallaTooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 250,
    content: @Composable () -> Unit,
) {
    val host = LocalTooltipHost.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val owner = remember { Any() }

    var anchorLayout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var visible by remember { mutableStateOf(false) }
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(delayMillis.toLong())
            if (isHovered) visible = true
        } else {
            visible = false
        }
    }

    LaunchedEffect(visible) {
        if (!visible) tooltipSize = IntSize.Zero
    }

    LaunchedEffect(visible, anchorLayout, tooltipSize, windowInfo.containerSize) {
        if (!visible || host == null) {
            host?.hide(owner)
            return@LaunchedEffect
        }
        val layout = anchorLayout?.takeIf { it.isAttached } ?: return@LaunchedEffect
        val bounds = layout.boundsInWindow()
        val windowSize = windowInfo.containerSize
        val gapPx = with(density) { 8.dp.roundToPx() }

        val chosen = if (tooltipSize == IntSize.Zero) {
            IntOffset(-10_000, -10_000)
        } else {
            computePosition(bounds, tooltipSize, windowSize.width, windowSize.height, gapPx)
        }

        host.show(
            owner = owner,
            content = {
                val colors = LocalYallaColors.current
                val tokens = LocalAppTokens.current

                val compactTextStyle = LocalTextStyle.current.copy(
                    lineHeight = 15.sp,
                    color = colors.textBase,
                )
                Column(
                    modifier = Modifier
                        .alpha(if (tooltipSize == IntSize.Zero) 0f else 1f)
                        .onSizeChanged { tooltipSize = it }
                        .background(colors.backgroundSecondary, tokens.shapeSmall)
                        .border(tokens.dividerThickness, colors.borderDefault, tokens.shapeSmall)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    CompositionLocalProvider(LocalTextStyle provides compactTextStyle) {
                        tooltip()
                    }
                }
            },
            offset = chosen,
        )
    }

    DisposableEffect(Unit) {
        onDispose { host?.hide(owner) }
    }

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .onGloballyPositioned { anchorLayout = it },
    ) {
        content()
    }
}

private fun computePosition(
    anchor: Rect,
    tooltipSize: IntSize,
    windowWidth: Int,
    windowHeight: Int,
    gap: Int,
): IntOffset {
    val tw = tooltipSize.width
    val th = tooltipSize.height

    val centerY = (anchor.top + anchor.height / 2 - th / 2).toInt()
        .coerceIn(0, (windowHeight - th).coerceAtLeast(0))
    val centerX = (anchor.left + anchor.width / 2 - tw / 2).toInt()
        .coerceIn(0, (windowWidth - tw).coerceAtLeast(0))

    return when {
        anchor.right.toInt() + gap + tw <= windowWidth -> IntOffset(
            x = anchor.right.toInt() + gap,
            y = centerY,
        )
        anchor.left.toInt() - gap - tw >= 0 -> IntOffset(
            x = anchor.left.toInt() - gap - tw,
            y = centerY,
        )
        anchor.top.toInt() - gap - th >= 0 -> IntOffset(
            x = centerX,
            y = anchor.top.toInt() - gap - th,
        )
        else -> IntOffset(
            x = centerX,
            y = anchor.bottom.toInt() + gap,
        )
    }
}
