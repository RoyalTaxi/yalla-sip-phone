package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

/** Parse "#RRGGBB" hex string to Compose [Color]. */
private fun parseHexColor(hex: String): Color {
    val sanitized = hex.removePrefix("#")
    val argb = sanitized.toLong(16) or 0xFF000000
    return Color(argb.toInt())
}

/**
 * Agent status selector that expands inline (no popup) to avoid z-order issues
 * with heavyweight SwingPanel (JCEF browser).
 *
 * Collapsed: colored dot + status name + arrow.
 * Expanded: horizontal row of colored dots (one per status) with tooltips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentStatusDropdown(
    currentStatus: AgentStatus,
    onStatusSelected: (AgentStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    var expanded by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "agent-status-toggle",
    ) { isExpanded ->
        if (isExpanded) {
            // Expanded: inline row of status dots
            Row(
                modifier = Modifier
                    .clip(tokens.shapeSmall)
                    .background(colors.backgroundBase)
                    .padding(horizontal = tokens.spacingXs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacingXs),
            ) {
                AgentStatus.entries.forEach { status ->
                    val statusColor = parseHexColor(status.colorHex)
                    val isCurrentStatus = status == currentStatus

                    TooltipBox(
                        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(status.displayName) } },
                        state = rememberTooltipState(),
                    ) {
                        Box(
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clip(CircleShape)
                                .then(
                                    if (isCurrentStatus) {
                                        Modifier
                                            .background(statusColor.copy(alpha = 0.2f), CircleShape)
                                            .padding(3.dp)
                                    } else {
                                        Modifier.padding(3.dp)
                                    },
                                )
                                .clickable {
                                    onStatusSelected(status)
                                    expanded = false
                                },
                        ) {
                            Box(
                                Modifier
                                    .size(if (isCurrentStatus) 12.dp else tokens.indicatorDot)
                                    .clip(CircleShape)
                                    .background(statusColor),
                            )
                        }
                    }
                }
            }
        } else {
            // Collapsed: dot + name + arrow
            Row(
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clip(tokens.shapeSmall)
                    .clickable { expanded = true }
                    .padding(horizontal = tokens.spacingXs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacingXs),
            ) {
                Box(
                    Modifier
                        .size(tokens.indicatorDot)
                        .clip(CircleShape)
                        .background(parseHexColor(currentStatus.colorHex)),
                )
                Text(
                    text = currentStatus.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textBase,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.textSubtle,
                )
            }
        }
    }
}
