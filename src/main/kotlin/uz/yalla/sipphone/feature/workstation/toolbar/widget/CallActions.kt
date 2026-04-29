package uz.yalla.sipphone.feature.workstation.toolbar.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.ui.component.YallaIconButton
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun CallActions(
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
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    AnimatedContent(
        targetState = callState::class,
        transitionSpec = {
            (fadeIn(animationSpec = tween(tokens.animFast)) togetherWith
                fadeOut(animationSpec = tween(tokens.animFast)))
        },
        modifier = modifier,
        label = "callActions",
    ) { _ ->
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.toolbarZoneGap),
    ) {
        when (callState) {
            is CallState.Idle -> {
                YallaIconButton(
                    icon = Icons.Filled.Call,
                    contentDescription = strings.buttonCall,
                    onClick = onCall,
                    enabled = !phoneInputEmpty,
                    containerColor = colors.brandPrimary,
                    contentColor = colors.onBrandPrimary,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.brandLight,
                )
                YallaIconButton(
                    icon = Icons.Filled.Mic,
                    contentDescription = strings.buttonMute,
                    onClick = {},
                    enabled = false,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.brandLight,
                )
                YallaIconButton(
                    icon = Icons.Filled.Pause,
                    contentDescription = strings.buttonHold,
                    onClick = {},
                    enabled = false,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.brandLight,
                )
            }

            is CallState.Ringing -> {
                if (!callState.isOutbound) {
                    YallaIconButton(
                        icon = Icons.Filled.Phone,
                        contentDescription = strings.buttonAnswer,
                        onClick = onAnswer,
                        containerColor = colors.brandPrimary,
                        contentColor = colors.onBrandPrimary,
                    )
                    YallaIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = strings.buttonReject,
                        onClick = onReject,
                        containerColor = colors.destructive,
                        contentColor = colors.onDestructive,
                    )
                } else {
                    YallaIconButton(
                        icon = Icons.Filled.CallEnd,
                        contentDescription = strings.buttonHangup,
                        onClick = onHangup,
                        containerColor = colors.destructive,
                        contentColor = colors.onDestructive,
                    )
                }
                RingingLabel()
            }

            is CallState.Active -> {
                YallaIconButton(
                    icon = Icons.Filled.CallEnd,
                    contentDescription = strings.buttonHangup,
                    onClick = onHangup,
                    containerColor = colors.destructive,
                    contentColor = colors.onDestructive,
                )
                YallaIconButton(
                    icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (callState.isMuted) strings.buttonUnmute else strings.buttonMute,
                    onClick = onToggleMute,
                    containerColor = if (callState.isMuted) colors.brandPrimary.copy(alpha = tokens.alphaLight) else colors.backgroundSecondary,
                    contentColor = if (callState.isMuted) colors.brandPrimary else colors.iconSubtle,
                )
                YallaIconButton(
                    icon = if (callState.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (callState.isOnHold) strings.buttonResume else strings.buttonHold,
                    onClick = onToggleHold,
                    containerColor = if (callState.isOnHold) colors.brandPrimary.copy(alpha = tokens.alphaLight) else colors.backgroundSecondary,
                    contentColor = if (callState.isOnHold) colors.brandPrimary else colors.iconSubtle,
                )
            }

            is CallState.Ending -> {}
        }
    }
    }
}

@Composable
private fun RingingLabel() {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    Text(
        text = strings.sipRinging,
        fontSize = tokens.textBase,
        color = colors.brandLight,
        modifier = Modifier
            .background(colors.brandPrimary.copy(alpha = tokens.alphaLight), tokens.shapeXs)
            .padding(horizontal = tokens.spacingSm, vertical = tokens.spacingXs),
    )
}
