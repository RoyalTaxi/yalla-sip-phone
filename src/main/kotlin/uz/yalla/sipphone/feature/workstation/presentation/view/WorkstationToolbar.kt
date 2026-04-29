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
import androidx.compose.ui.Alignment
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
                activeCallAccountId = state.activeCallAccountId.takeIf { !it.isNullOrEmpty() },
                onChipClick = { onIntent(WorkstationIntent.OnSipChipClick(it)) },
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            VerticalDivider()
            Spacer(Modifier.width(tokens.toolbarZoneGap))

            UpdateBadge(
                state = state.updateState,
                onClick = { onIntent(WorkstationIntent.ShowUpdateDialog) },
            )

            SettingsToggleButton(
                visible = state.settingsVisible,
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
}

@Composable
private fun SettingsToggleButton(visible: Boolean, onClick: () -> Unit) {
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
