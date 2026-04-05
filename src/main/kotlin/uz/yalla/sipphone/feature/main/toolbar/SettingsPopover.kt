package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

/**
 * Settings button that opens an AlertDialog instead of a Popup.
 * AlertDialog creates a real OS-level dialog window, which avoids z-order
 * issues with heavyweight SwingPanel (JCEF browser).
 */
@Composable
fun SettingsPopover(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(32.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = Strings.SETTINGS_TITLE,
                modifier = Modifier.size(20.dp),
                tint = colors.textSubtle,
            )
        }

        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = {
                    Text(
                        text = Strings.SETTINGS_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textBase,
                    )
                },
                text = {
                    Column(modifier = Modifier.widthIn(min = 200.dp)) {
                        // Theme toggle row
                        Row(
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onThemeToggle() }
                                .padding(vertical = tokens.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                        ) {
                            Text(
                                text = Strings.SETTINGS_THEME,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textBase,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (isDarkTheme) Strings.SETTINGS_THEME_DARK
                                else Strings.SETTINGS_THEME_LIGHT,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSubtle,
                            )
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = null,
                            )
                        }

                        HorizontalDivider()

                        // Version
                        Box(
                            modifier = Modifier.padding(vertical = tokens.spacingSm),
                        ) {
                            Text(
                                text = "v1.0.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSubtle,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            expanded = false
                            onLogout()
                        },
                    ) {
                        Text(
                            text = Strings.SETTINGS_LOGOUT,
                            color = colors.errorText,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { expanded = false }) {
                        Text("Close")
                    }
                },
            )
        }
    }
}
