package uz.yalla.sipphone.feature.main.toolbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.yalla.sipphone.domain.SipAccount
import uz.yalla.sipphone.domain.SipAccountState
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalYallaColors

/**
 * SIP account chips row, right-aligned.
 *
 * Each chip shows account name with visual state based on [SipAccountState].
 * During active call, the active account chip is highlighted, others are muted.
 *
 * Click toggles connect/disconnect. Reconnecting chips are not clickable.
 */
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
            SipChip(
                account = account,
                isActiveCall = activeCallAccountId == account.id,
                isMutedByCall = activeCallAccountId != null && activeCallAccountId != account.id,
                onClick = { onChipClick(account.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SipChip(
    account: SipAccount,
    isActiveCall: Boolean,
    isMutedByCall: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalYallaColors.current
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(6.dp)

    val isClickable = account.state !is SipAccountState.Reconnecting

    // Compute chip colors based on state
    val (bgColor, borderColor, textColor) = when {
        // Active call chip — brand solid
        isActiveCall -> Triple(
            colors.brandPrimary,
            colors.brandPrimary,
            Color.White,
        )
        // Muted by active call (connected but not active)
        isMutedByCall && account.state is SipAccountState.Connected -> Triple(
            colors.surfaceMuted,
            colors.borderDefault,
            colors.textSubtle,
        )
        // Normal connected
        account.state is SipAccountState.Connected -> Triple(
            colors.brandPrimary.copy(alpha = 0.12f),
            colors.brandPrimary.copy(alpha = 0.3f),
            colors.brandLight,
        )
        // Reconnecting
        account.state is SipAccountState.Reconnecting -> Triple(
            colors.statusWarning.copy(alpha = 0.1f),
            colors.statusWarning.copy(alpha = 0.25f),
            colors.statusWarning,
        )
        // Disconnected
        else -> Triple(
            colors.destructive.copy(alpha = 0.1f),
            colors.destructive.copy(alpha = 0.25f),
            colors.destructive,
        )
    }

    val prefix = if (isActiveCall) "\u25CF " else ""

    // Tooltip content
    val statusText = when (account.state) {
        is SipAccountState.Connected -> strings.sipConnected
        is SipAccountState.Reconnecting -> strings.sipReconnecting
        is SipAccountState.Disconnected -> strings.sipDisconnected
    }
    val statusColor = when (account.state) {
        is SipAccountState.Connected -> colors.brandPrimary
        is SipAccountState.Reconnecting -> colors.statusWarning
        is SipAccountState.Disconnected -> colors.destructive
    }

    TooltipArea(
        tooltip = {
            Column(
                modifier = Modifier
                    .background(colors.backgroundSecondary, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.borderDefault, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = account.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textBase,
                )
                Text(
                    text = account.credentials.username,
                    fontSize = 11.sp,
                    color = colors.textSubtle,
                )
                Text(
                    text = "${account.credentials.server}:${account.credentials.port}",
                    fontSize = 11.sp,
                    color = colors.textSubtle,
                )
                if (account.credentials.transport != "UDP") {
                    Text(
                        text = account.credentials.transport,
                        fontSize = 11.sp,
                        color = colors.textSubtle,
                    )
                }
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
                if (account.state is SipAccountState.Disconnected) {
                    Text(
                        text = strings.sipReconnectHint,
                        fontSize = 10.sp,
                        color = colors.destructive,
                    )
                }
            }
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, (-16).dp)),
        delayMillis = 300,
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .clip(shape)
                .background(bgColor, shape)
                .border(1.dp, borderColor, shape)
                .then(
                    if (isClickable) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$prefix${account.name}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}
