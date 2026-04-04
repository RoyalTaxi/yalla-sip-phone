package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalExtendedColors
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun CallControls(
    callState: CallState,
    phoneInputEmpty: Boolean,
    onCall: () -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
    ) {
        when (callState) {
            is CallState.Idle -> {
                Button(
                    onClick = onCall,
                    enabled = !phoneInputEmpty,
                    shape = tokens.shapeSmall,
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.iconSmall),
                    )
                    Spacer(Modifier.width(tokens.spacingXs))
                    Text(Strings.BUTTON_CALL)
                }
            }

            is CallState.Ringing -> {
                if (!callState.isOutbound) {
                    Button(
                        onClick = onAnswer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalExtendedColors.current.success,
                        ),
                        shape = tokens.shapeSmall,
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSmall),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text(Strings.BUTTON_ANSWER)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        shape = tokens.shapeSmall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.errorText,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSmall),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text(Strings.BUTTON_REJECT)
                    }
                } else {
                    OutlinedButton(
                        onClick = onHangup,
                        shape = tokens.shapeSmall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.errorText,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.CallEnd,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSmall),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text(Strings.BUTTON_CANCEL)
                    }
                }
            }

            is CallState.Active -> {
                OutlinedButton(
                    onClick = onToggleMute,
                    shape = tokens.shapeSmall,
                    colors = if (callState.isMuted) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.callMuted.copy(alpha = 0.12f),
                            contentColor = colors.callMuted,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Icon(
                        Icons.Filled.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.iconSmall),
                    )
                    Spacer(Modifier.width(tokens.spacingXs))
                    Text(if (callState.isMuted) Strings.BUTTON_UNMUTE else Strings.BUTTON_MUTE)
                }

                if (callState.isOnHold) {
                    Button(
                        onClick = onToggleHold,
                        shape = tokens.shapeSmall,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSmall),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text(Strings.BUTTON_RESUME)
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleHold,
                        shape = tokens.shapeSmall,
                    ) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSmall),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text(Strings.BUTTON_HOLD)
                    }
                }

                OutlinedButton(
                    onClick = onHangup,
                    shape = tokens.shapeSmall,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.errorText,
                    ),
                ) {
                    Icon(
                        Icons.Filled.CallEnd,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.iconSmall),
                    )
                    Spacer(Modifier.width(tokens.spacingXs))
                    Text(Strings.BUTTON_END)
                }
            }

            is CallState.Ending -> {
                // No controls during ending transition
            }
        }
    }
}
