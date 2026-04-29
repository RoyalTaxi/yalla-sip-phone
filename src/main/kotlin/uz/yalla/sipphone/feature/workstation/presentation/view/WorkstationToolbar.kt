package uz.yalla.sipphone.feature.workstation.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import uz.yalla.sipphone.domain.call.CallState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationIntent
import uz.yalla.sipphone.feature.workstation.presentation.intent.WorkstationState
import uz.yalla.sipphone.feature.workstation.toolbar.widget.AgentStatusButton
import uz.yalla.sipphone.feature.workstation.toolbar.widget.CallActions
import uz.yalla.sipphone.feature.workstation.toolbar.widget.CallTimer
import uz.yalla.sipphone.feature.workstation.toolbar.widget.PhoneField
import uz.yalla.sipphone.feature.workstation.toolbar.widget.SipChipRow
import uz.yalla.sipphone.feature.workstation.update.UpdateBadge
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
internal fun WorkstationToolbar(
    state: WorkstationState,
    onIntent: (WorkstationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.toolbarHeight)
            .shadow(elevation = tokens.elevationLow, shape = RectangleShape)
            .background(colors.backgroundBase),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.toolbarHeight)
                .padding(horizontal = tokens.toolbarPaddingH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LeftZone(state, onIntent)
            RightZone(state, onIntent)
        }
    }
}

@Composable
private fun RowScope.LeftZone(
    state: WorkstationState,
    onIntent: (WorkstationIntent) -> Unit,
) {
    val tokens = LocalAppTokens.current
    var phoneInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.call) {
        when (val c = state.call) {
            is CallState.Ringing -> if (!c.isOutbound) phoneInput = c.callerNumber
            is CallState.Idle -> phoneInput = ""
            else -> Unit
        }
    }

    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.toolbarZoneGap),
    ) {
        AgentStatusButton(
            currentStatus = state.agent,
            onStatusSelected = { onIntent(WorkstationIntent.SetAgentStatus(it)) },
        )
        PhoneField(
            phoneNumber = phoneInput,
            onValueChange = { phoneInput = it },
            callState = state.call,
        )
        VerticalDivider()
        CallActions(
            callState = state.call,
            phoneInputEmpty = phoneInput.isBlank(),
            onCall = { onIntent(WorkstationIntent.SubmitCall(phoneInput)) },
            onAnswer = { onIntent(WorkstationIntent.AnswerCall) },
            onReject = { onIntent(WorkstationIntent.RejectCall) },
            onHangup = { onIntent(WorkstationIntent.HangupCall) },
            onToggleMute = { onIntent(WorkstationIntent.ToggleMute) },
            onToggleHold = { onIntent(WorkstationIntent.ToggleHold) },
        )
        CallTimer(duration = state.callDuration)
    }
}

@Composable
private fun RightZone(
    state: WorkstationState,
    onIntent: (WorkstationIntent) -> Unit,
) {
    val tokens = LocalAppTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.toolbarZoneGap),
    ) {
        SipChipRow(
            accounts = state.accounts,
            activeCallAccountId = state.activeCallAccountId.takeIf { !it.isNullOrEmpty() },
            onChipClick = { onIntent(WorkstationIntent.OnSipChipClick(it)) },
        )
        VerticalDivider()
        UpdateBadge(
            state = state.updateState,
            onClick = { onIntent(WorkstationIntent.ShowUpdateDialog) },
        )
        SettingsToggleButton(
            onClick = {
                val nextIntent = if (state.settingsVisible) {
                    WorkstationIntent.CloseSettings
                } else {
                    WorkstationIntent.OpenSettings
                }
                onIntent(nextIntent)
            },
        )
    }
}

@Composable
private fun SettingsToggleButton(onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(tokens.iconButtonSize)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(tokens.iconDefault),
            tint = colors.iconSubtle,
        )
    }
}

@Composable
private fun VerticalDivider() {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Box(
        Modifier
            .height(tokens.dividerHeight)
            .width(tokens.dividerThickness)
            .background(colors.borderDefault),
    )
}
