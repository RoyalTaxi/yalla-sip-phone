package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Cross-platform hover feedback for dropdowns, popups, and list rows.
 *
 * Material 3's default `clickable` ripple is near-invisible on dark surfaces with custom
 * backgrounds, so Compose Desktop apps frequently ship with "hover does nothing" — which is
 * exactly the complaint dispatcher operators had about this app. This modifier restores a
 * visible hover highlight that works reliably in both themes.
 *
 * Intended use: apply in place of `.clickable { }` on Box/Row that should react to hover.
 * Adds pointer cursor automatically. Ripple is disabled; the background change IS the feedback.
 */
@Composable
fun Modifier.hoverClickable(
    hoverBackground: Color,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor = if (isHovered && enabled) hoverBackground else Color.Transparent
    Modifier
        .background(bgColor, shape)
        .hoverable(interactionSource, enabled = enabled)
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}
