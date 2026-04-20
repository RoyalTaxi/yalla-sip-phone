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

/**
 * In-window hover tooltip. Placed to the LEFT, RIGHT or TOP of the anchor — never below,
 * never under the cursor.
 *
 * **Why this design.** Every Popup-based approach (native OS window) flickered on macOS:
 * the popup took mouse ownership away from the main window, the anchor saw mouse-exit,
 * popup dismissed, feedback loop. Placing the popup below-anchor with a gap didn't fix it
 * — the focus-transition blip is non-deterministic (20–500 ms depending on GPU / macOS
 * version), and no fixed debounce covers all of them.
 *
 * **What this does.** Renders the tooltip as a regular Compose overlay inside the main
 * window via [TooltipHost]. No native window = no mouse-ownership transfer = no flicker.
 * Placement prefers RIGHT of anchor (fits best in typical toolbar layouts), falls back to
 * LEFT if right-edge would clip, then TOP, then BELOW as last resort.
 *
 * **Caveat.** In-window Compose content cannot draw above heavyweight AWT children (JCEF's
 * Chromium canvas). If the chosen placement overlaps a heavyweight child, the tooltip will
 * be hidden behind it. For the toolbar chips this works because the placement algorithm
 * keeps the tooltip in the toolbar's Compose-only strip. If a tooltip anchor is placed
 * elsewhere, the chosen direction should keep it in pure-Compose space.
 *
 * **Size measurement.** The tooltip's size isn't known before its first render, so we
 * draw it offscreen with alpha=0 on the first frame to let it self-measure, then on the
 * next frame snap it to the computed position with alpha=1. Visually imperceptible.
 */
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

    // SHOW/HIDE based on Compose hover. Reliable for in-window rendering (no native popup
    // steals focus, so hover state doesn't glitch).
    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(delayMillis.toLong())
            if (isHovered) visible = true
        } else {
            visible = false
        }
    }

    // Reset the cached tooltip size each time we hide → fresh measurement next show, in case
    // content changed.
    LaunchedEffect(visible) {
        if (!visible) tooltipSize = IntSize.Zero
    }

    // Compute position and push the tooltip into the host.
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
            // Pre-measurement render — offscreen so user doesn't see it flash.
            IntOffset(-10_000, -10_000)
        } else {
            computePosition(bounds, tooltipSize, windowSize.width, windowSize.height, gapPx)
        }

        host.show(
            owner = owner,
            content = {
                val colors = LocalYallaColors.current
                val tokens = LocalAppTokens.current
                // Tight text style for the tooltip body. Material default line-height is
                // ~1.5x font size which bloats multi-line tooltips; 1.15x is compact but
                // still readable.
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

/**
 * Placement preference: RIGHT → LEFT → TOP → BELOW (fallback). Centers the tooltip on the
 * anchor along the perpendicular axis, then clamps to window bounds so we never draw off-screen.
 */
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
