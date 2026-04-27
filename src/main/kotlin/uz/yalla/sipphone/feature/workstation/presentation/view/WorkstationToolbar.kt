package uz.yalla.sipphone.feature.workstation.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.data.update.manager.UpdateManager
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
    updateManager: UpdateManager,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    val updateUiState by updateManager.state.collectAsState()

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
        ) {
            AgentStatusButton(
                currentStatus = state.agent,
                onStatusSelected = { onIntent(WorkstationIntent.SetAgentStatus(it)) },
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))

            PhoneField(
                phoneNumber = state.phoneInput,
                onValueChange = { onIntent(WorkstationIntent.SetPhoneInput(it)) },
                callState = state.call,
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            VerticalDivider()
            Spacer(Modifier.width(tokens.toolbarZoneGap))

            CallActions(
                callState = state.call,
                phoneInputEmpty = state.phoneInput.isBlank(),
                onCall = { onIntent(WorkstationIntent.SubmitCall(state.phoneInput)) },
                onAnswer = { onIntent(WorkstationIntent.AnswerCall) },
                onReject = { onIntent(WorkstationIntent.RejectCall) },
                onHangup = { onIntent(WorkstationIntent.HangupCall) },
                onToggleMute = { onIntent(WorkstationIntent.ToggleMute) },
                onToggleHold = { onIntent(WorkstationIntent.ToggleHold) },
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            CallTimer(duration = state.callDuration)

            Spacer(Modifier.weight(1f))

            SipChipRow(
                accounts = state.accounts,
                activeCallAccountId = state.activeCallAccountId.takeIf { it?.isNotEmpty() == true },
                onChipClick = { onIntent(WorkstationIntent.OnSipChipClick(it)) },
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            VerticalDivider()
            Spacer(Modifier.width(tokens.toolbarZoneGap))

            UpdateBadge(
                state = updateManager.state,
                onClick = { onIntent(WorkstationIntent.ShowUpdateDialog) },
            )

            IconButton(
                onClick = {
                    if (state.settingsVisible) onIntent(WorkstationIntent.CloseSettings)
                    else onIntent(WorkstationIntent.OpenSettings)
                },
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
    }
}

@Composable
private fun VerticalDivider() {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Box(
        Modifier
            .height(tokens.dividerHeight)
            .width(1.dp)
            .background(colors.borderDefault)
    )
}

