package uz.yalla.sipphone.feature.main.toolbar

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import uz.yalla.sipphone.domain.AgentStatus
import uz.yalla.sipphone.ui.theme.LocalAppTokens

/** Parse "#RRGGBB" hex string to Compose [Color]. */
private fun parseHexColor(hex: String): Color {
    val sanitized = hex.removePrefix("#")
    val argb = sanitized.toLong(16) or 0xFF000000
    return Color(argb.toInt())
}

@Composable
fun AgentStatusDropdown(
    currentStatus: AgentStatus,
    onStatusSelected: (AgentStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = tokens.spacingSm, vertical = tokens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacingXs),
        ) {
            Box(
                Modifier
                    .size(tokens.qualityDotSize)
                    .clip(CircleShape)
                    .background(parseHexColor(currentStatus.colorHex)),
            )
            Text(
                text = currentStatus.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(tokens.iconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AgentStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                        ) {
                            Box(
                                Modifier
                                    .size(tokens.qualityDotSize)
                                    .clip(CircleShape)
                                    .background(parseHexColor(status.colorHex)),
                            )
                            Text(
                                text = status.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        onStatusSelected(status)
                        expanded = false
                    },
                )
            }
        }
    }
}
