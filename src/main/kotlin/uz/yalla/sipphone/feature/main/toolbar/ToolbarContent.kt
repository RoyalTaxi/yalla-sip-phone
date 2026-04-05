package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.yalla.sipphone.domain.CallState
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors
import uz.yalla.sipphone.util.formatDuration

@Composable
fun ToolbarContent(
    component: ToolbarComponent,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current

    val callState by component.callState.collectAsState()
    val agentStatus by component.agentStatus.collectAsState()
    val phoneInput by component.phoneInput.collectAsState()

    // Call timer
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.toolbarHeight)
            .background(colors.backgroundSecondary),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.toolbarHeight)
                .padding(horizontal = tokens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zone A: Agent Status Dropdown
            AgentStatusDropdown(
                currentStatus = agentStatus,
                onStatusSelected = component::setAgentStatus,
            )

            Spacer(Modifier.width(tokens.spacingSm))

            // Vertical divider between Zone A and Zone B
            Box(
                Modifier
                    .width(tokens.dividerThickness)
                    .height(tokens.dividerHeight)
                    .background(colors.backgroundTertiary),
            )

            Spacer(Modifier.width(tokens.spacingSm))

            // Zone B: Phone input or call info (flexible width)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                ZoneBContent(
                    callState = callState,
                    phoneInput = phoneInput,
                    onPhoneInputChange = component::updatePhoneInput,
                    callDuration = callDuration,
                )
            }

            Spacer(Modifier.width(tokens.spacingSm))

            // Zone C: Call action buttons (~200dp)
            CallControls(
                callState = callState,
                phoneInputEmpty = phoneInput.isBlank(),
                onCall = { component.makeCall(phoneInput) },
                onAnswer = component::answerCall,
                onReject = component::rejectCall,
                onHangup = component::hangupCall,
                onToggleMute = component::toggleMute,
                onToggleHold = component::toggleHold,
            )

            Spacer(Modifier.width(tokens.spacingSm))

            // Zone D: Settings gear (40dp)
            SettingsPopover(
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onLogout = onLogout,
            )

            Spacer(Modifier.width(tokens.spacingXs))

            // Zone E: Call quality indicator (56dp)
            CallQualityIndicator(callState = callState)
        }

        // Bottom border — visible separator between toolbar and webview
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(tokens.toolbarDividerHeight)
                .background(colors.borderDisabled),
        )
    }
}

@Composable
private fun ZoneBContent(
    callState: CallState,
    phoneInput: String,
    onPhoneInputChange: (String) -> Unit,
    callDuration: Long,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current

    when (callState) {
        is CallState.Idle -> {
            OutlinedTextField(
                value = phoneInput,
                onValueChange = onPhoneInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(Strings.PLACEHOLDER_PHONE) },
                singleLine = true,
                shape = tokens.shapeSmall,
            )
        }

        is CallState.Ringing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = callState.callerNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textBase,
                )
                if (callState.isOutbound) {
                    Spacer(Modifier.width(tokens.spacingSm))
                    Text(
                        text = Strings.STATUS_RINGING,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSubtle,
                    )
                }
            }
        }

        is CallState.Active -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = callState.remoteNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textBase,
                )
                Spacer(Modifier.width(tokens.spacingSm))
                Text(
                    text = formatDuration(callDuration),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (callState.isOnHold) {
                        colors.textSubtle
                    } else {
                        colors.textBase
                    },
                )
            }
        }

        is CallState.Ending -> {
            Text(
                text = Strings.STATUS_ENDING_CALL,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSubtle,
            )
        }
    }
}
