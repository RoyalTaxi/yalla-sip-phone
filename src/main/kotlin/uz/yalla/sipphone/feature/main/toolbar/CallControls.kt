package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        shape = tokens.shapeSmall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.errorText,
                        ),
                        border = BorderStroke(1.dp, colors.errorIndicator),
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
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        shape = tokens.shapeSmall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.errorText,
                        ),
                        border = BorderStroke(1.dp, colors.errorIndicator),
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    shape = tokens.shapeSmall,
                    colors = if (callState.isMuted) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.callMuted.copy(alpha = 0.15f),
                            contentColor = colors.callMuted,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = if (callState.isMuted) {
                        BorderStroke(1.dp, colors.callMuted)
                    } else {
                        ButtonDefaults.outlinedButtonBorder(enabled = true)
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
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    shape = tokens.shapeSmall,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.errorText,
                    ),
                    border = BorderStroke(1.dp, colors.errorIndicator),
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
