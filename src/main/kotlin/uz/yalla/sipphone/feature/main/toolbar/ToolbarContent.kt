package uz.yalla.sipphone.feature.main.toolbar

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
import uz.yalla.sipphone.feature.main.toolbar.widget.AgentStatusButton
import uz.yalla.sipphone.feature.main.toolbar.widget.CallActions
import uz.yalla.sipphone.feature.main.toolbar.widget.CallTimer
import uz.yalla.sipphone.feature.main.toolbar.widget.PhoneField
import uz.yalla.sipphone.feature.main.toolbar.widget.SipChipRow
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.feature.main.update.UpdateBadge
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun ToolbarContent(
    component: ToolbarComponent,
    isDarkTheme: Boolean,
    locale: String,
    agentInfo: AgentInfo?,
    onThemeToggle: () -> Unit,
    onLocaleChange: (String) -> Unit,
    onLogout: () -> Unit,
    updateManager: UpdateManager? = null,
    onUpdateBadgeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current

    val ui by component.state.collectAsState()
    val callState = ui.call
    val agentStatus = ui.agent
    val phoneInput = ui.phoneInput
    val accounts = ui.accounts
    val callDuration = ui.callDuration
    val activeCallAccountId = ui.activeCallAccountId

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
                currentStatus = agentStatus,
                onStatusSelected = component::setAgentStatus,
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))

            PhoneField(
                phoneNumber = phoneInput,
                onValueChange = component::updatePhoneInput,
                callState = callState,
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            VerticalDivider()
            Spacer(Modifier.width(tokens.toolbarZoneGap))

            CallActions(
                callState = callState,
                phoneInputEmpty = phoneInput.isBlank(),
                onCall = { component.makeCall(phoneInput) },
                onAnswer = component::answerCall,
                onReject = component::rejectCall,
                onHangup = component::hangupCall,
                onToggleMute = component::toggleMute,
                onToggleHold = component::toggleHold,
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            CallTimer(duration = callDuration)

            Spacer(Modifier.weight(1f))

            SipChipRow(
                accounts = accounts,
                activeCallAccountId = activeCallAccountId.takeIf { it?.isNotEmpty() == true },
                onChipClick = component::onSipChipClick,
            )

            Spacer(Modifier.width(tokens.toolbarZoneGap))
            VerticalDivider()
            Spacer(Modifier.width(tokens.toolbarZoneGap))

            if (updateManager != null) {
                UpdateBadge(
                    state = updateManager.state,
                    onClick = onUpdateBadgeClick,
                )
            }

            IconButton(
                onClick = {
                    if (ui.settingsVisible) component.closeSettings()
                    else component.openSettings()
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
            .width(tokens.dividerThickness)
            .height(tokens.dividerHeight)
            .background(colors.borderDefault),
    )
}
