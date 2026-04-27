package uz.yalla.sipphone.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

@Composable
fun Modifier.hoverClickable(
    hoverBackground: Color,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val target = if (isHovered && enabled) hoverBackground else Color.Transparent
    val bg by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 90),
        label = "hoverBg",
    )
    return this
        .background(bg, shape)
        .hoverable(interactionSource, enabled = enabled)
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}
