package uz.yalla.sipphone.feature.workstation.toolbar.widget

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountState
import uz.yalla.sipphone.ui.component.YallaTooltip
import uz.yalla.sipphone.ui.component.hoverClickable
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.AppTokens
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors
import uz.yalla.sipphone.ui.theme.YallaColors

@Composable
fun SipChipRow(
    accounts: List<SipAccount>,
    activeCallAccountId: String?,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.forEach { account ->

            key(account.id) {
                SipChip(
                    account = account,
                    isActiveCall = activeCallAccountId == account.id,
                    isMutedByCall = activeCallAccountId != null && activeCallAccountId != account.id,
                    onClick = { onChipClick(account.id) },
                )
            }
        }
    }
}

@Composable
private fun SipChip(
    account: SipAccount,
    isActiveCall: Boolean,
    isMutedByCall: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    val isClickable = account.state !is SipAccountState.Reconnecting
    val chipStyle = remember(account.state, isActiveCall, isMutedByCall, colors, tokens) {
        resolveChipStyle(colors, tokens, account.state, isActiveCall, isMutedByCall)
    }

    val transition = updateTransition(targetState = chipStyle, label = "chipStyle")
    val bgColor by transition.animateColor(
        transitionSpec = { tween(tokens.animFast) },
        label = "chipBg",
    ) { it.bgColor }
    val borderColor by transition.animateColor(
        transitionSpec = { tween(tokens.animFast) },
        label = "chipBorder",
    ) { it.borderColor }
    val textColor by transition.animateColor(
        transitionSpec = { tween(tokens.animFast) },
        label = "chipText",
    ) { it.textColor }

    val statusText = when (account.state) {
        is SipAccountState.Connected -> strings.sipConnected
        is SipAccountState.Reconnecting -> strings.sipReconnecting
        is SipAccountState.Disconnected -> strings.sipDisconnected
    }
    val statusColor = when (account.state) {
        is SipAccountState.Connected -> colors.statusOnline
        is SipAccountState.Reconnecting -> colors.statusWarning
        is SipAccountState.Disconnected -> colors.destructive
    }

    val label = remember(isActiveCall, account.name) {
        if (isActiveCall) "● ${account.name}" else account.name
    }

    YallaTooltip(
        tooltip = {
            Text(account.credentials.username, fontSize = tokens.textSm, color = colors.textSubtle)
            Text("${account.credentials.server}:${account.credentials.port}", fontSize = tokens.textSm, color = colors.textSubtle)
            if (account.credentials.transport != "UDP") {
                Text(account.credentials.transport, fontSize = tokens.textSm, color = colors.textSubtle)
            }
            Text(statusText, fontSize = tokens.textSm, fontWeight = FontWeight.Medium, color = statusColor)
            if (account.state is SipAccountState.Disconnected) {
                Text(strings.sipReconnectHint, fontSize = tokens.textXs, color = colors.destructive)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .height(tokens.chipHeight)
                .clip(tokens.shapeXs)
                .background(bgColor, tokens.shapeXs)
                .border(tokens.dividerThickness, borderColor, tokens.shapeXs)
                .hoverClickable(
                    hoverBackground = chipStyle.borderColor.copy(alpha = tokens.alphaMedium),
                    shape = tokens.shapeXs,
                    enabled = isClickable,
                    onClick = onClick,
                )
                .padding(horizontal = tokens.spacingMdSm - 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = tokens.textBase,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@androidx.compose.runtime.Immutable
private data class ChipStyle(val bgColor: Color, val borderColor: Color, val textColor: Color)

private fun resolveChipStyle(
    colors: YallaColors,
    tokens: AppTokens,
    state: SipAccountState,
    isActiveCall: Boolean,
    isMutedByCall: Boolean,
): ChipStyle = when {
    isActiveCall -> ChipStyle(colors.brandPrimary, colors.brandPrimary, Color.White)
    isMutedByCall && state is SipAccountState.Connected ->
        ChipStyle(colors.surfaceMuted, colors.borderDefault, colors.textSubtle)
    state is SipAccountState.Connected -> ChipStyle(
        colors.statusOnline.copy(alpha = tokens.alphaMuted),
        colors.statusOnline.copy(alpha = tokens.alphaMedium),
        colors.statusOnline,
    )
    state is SipAccountState.Reconnecting -> ChipStyle(
        colors.statusWarning.copy(alpha = tokens.alphaSubtle),
        colors.statusWarning.copy(alpha = tokens.alphaBorder),
        colors.statusWarning,
    )
    else -> ChipStyle(
        colors.destructive.copy(alpha = tokens.alphaSubtle),
        colors.destructive.copy(alpha = tokens.alphaBorder),
        colors.destructive,
    )
}
