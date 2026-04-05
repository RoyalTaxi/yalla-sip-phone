package uz.yalla.sipphone.feature.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.domain.RegistrationState
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalExtendedColors
import uz.yalla.sipphone.util.formatDuration

@Composable
fun DialerScreen(component: DialerComponent) {
    val tokens = LocalAppTokens.current
    val registrationState by component.registrationState.collectAsState()
    val callState by component.callState.collectAsState()

    var phoneNumber by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var callDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(callState) {
        if (callState is CallState.Active) {
            callDuration = 0
            while (isActive) {
                delay(1000)
                callDuration++
            }
        } else {
            callDuration = 0
        }
    }

    when (registrationState) {
        is RegistrationState.Registered -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Spacebar &&
                            callState is CallState.Ringing &&
                            !(callState as CallState.Ringing).isOutbound &&
                            !isInputFocused
                        ) {
                            component.answerCall()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Surface(tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = tokens.spacingMd, vertical = tokens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(tokens.indicatorDot).clip(CircleShape)
                                .background(LocalExtendedColors.current.success),
                        )
                        Spacer(Modifier.width(tokens.spacingSm))
                        Text(
                            (registrationState as RegistrationState.Registered).server,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                when (val state = callState) {
                    is CallState.Idle -> IdleRow(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        onCall = { if (phoneNumber.isNotBlank()) component.makeCall(phoneNumber) },
                        onDisconnect = component::disconnect,
                        onFocusChanged = { isInputFocused = it },
                    )
                    is CallState.Ringing -> if (state.isOutbound) {
                        OutboundRingingRow(
                            callerNumber = state.callerNumber,
                            onCancel = component::hangupCall,
                        )
                    } else {
                        RingingRow(
                            callerNumber = state.callerNumber,
                            callerName = state.callerName,
                            onAnswer = component::answerCall,
                            onReject = component::hangupCall,
                        )
                    }
                    is CallState.Active -> ActiveCallRow(
                        remoteNumber = state.remoteNumber,
                        remoteName = state.remoteName,
                        duration = callDuration,
                        isMuted = state.isMuted,
                        isOnHold = state.isOnHold,
                        onToggleMute = component::toggleMute,
                        onToggleHold = component::toggleHold,
                        onHangup = component::hangupCall,
                    )
                    is CallState.Ending -> EndingRow()
                }
            }
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    Strings.STATUS_CONNECTION_LOST,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun IdleRow(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onCall: () -> Unit,
    onDisconnect: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
    ) {
        Text(
            Strings.STATUS_READY,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            placeholder = { Text(Strings.PLACEHOLDER_PHONE) },
            singleLine = true,
            shape = tokens.shapeSmall,
        )
        Button(
            onClick = onCall,
            enabled = phoneNumber.isNotBlank(),
            shape = tokens.shapeSmall,
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
            Spacer(Modifier.width(tokens.spacingXs))
            Text(Strings.BUTTON_CALL)
        }
        TextButton(onClick = onDisconnect) {
            Text(Strings.BUTTON_DISCONNECT, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OutboundRingingRow(
    callerNumber: String,
    onCancel: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                Strings.STATUS_CALLING,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(tokens.spacingXs))
            Text(
                callerNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedButton(
            onClick = onCancel,
            shape = tokens.shapeSmall,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
            Spacer(Modifier.width(tokens.spacingXs))
            Text(Strings.BUTTON_CANCEL)
        }
    }
}

@Composable
private fun RingingRow(
    callerNumber: String,
    callerName: String?,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                Strings.STATUS_INCOMING_CALL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(tokens.spacingXs))
            Text(
                callerNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (callerName != null) {
                Text(
                    callerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
            Button(
                onClick = onAnswer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalExtendedColors.current.success,
                ),
                shape = tokens.shapeSmall,
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                Spacer(Modifier.width(6.dp))
                Text(Strings.BUTTON_ANSWER)
                Text(
                    Strings.STATUS_SPACE_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalExtendedColors.current.onSuccess.copy(alpha = tokens.alphaHint),
                )
            }
            OutlinedButton(
                onClick = onReject,
                shape = tokens.shapeSmall,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                Spacer(Modifier.width(tokens.spacingXs))
                Text(Strings.BUTTON_REJECT)
            }
        }
    }
}

@Composable
private fun ActiveCallRow(
    remoteNumber: String,
    remoteName: String?,
    duration: Long,
    isMuted: Boolean,
    isOnHold: Boolean,
    onToggleMute: () -> Unit,
    onToggleHold: () -> Unit,
    onHangup: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(tokens.indicatorDotSmall).clip(CircleShape).background(
                        if (isOnHold) MaterialTheme.colorScheme.tertiary
                        else LocalExtendedColors.current.success,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isOnHold) Strings.STATUS_ON_HOLD else Strings.STATUS_ACTIVE,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnHold) MaterialTheme.colorScheme.tertiary
                    else LocalExtendedColors.current.success,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.width(tokens.spacingSm))
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isOnHold) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(tokens.spacingXs))
            Text(
                remoteNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (remoteName != null) {
                Text(
                    remoteName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
        ) {
            OutlinedButton(
                onClick = onToggleMute,
                shape = tokens.shapeSmall,
                colors = if (isMuted) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) else ButtonDefaults.outlinedButtonColors(),
            ) {
                Icon(Icons.Filled.MicOff, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                Spacer(Modifier.width(tokens.spacingXs))
                Text(if (isMuted) Strings.BUTTON_UNMUTE else Strings.BUTTON_MUTE)
            }

            if (isOnHold) {
                Button(
                    onClick = onToggleHold,
                    shape = tokens.shapeSmall,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                    Spacer(Modifier.width(tokens.spacingXs))
                    Text(Strings.BUTTON_RESUME)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleHold,
                    shape = tokens.shapeSmall,
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                    Spacer(Modifier.width(tokens.spacingXs))
                    Text(Strings.BUTTON_HOLD)
                }
            }

            Box(
                Modifier
                    .width(tokens.dividerThickness)
                    .height(tokens.dividerHeight)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            OutlinedButton(
                onClick = onHangup,
                shape = tokens.shapeSmall,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(tokens.iconSmall))
                Spacer(Modifier.width(tokens.spacingXs))
                Text(Strings.BUTTON_END)
            }
        }
    }
}

@Composable
private fun EndingRow() {
    val tokens = LocalAppTokens.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacingMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Strings.STATUS_ENDING_CALL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
