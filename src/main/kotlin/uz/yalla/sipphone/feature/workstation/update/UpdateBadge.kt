package uz.yalla.sipphone.feature.workstation.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.yalla.sipphone.domain.update.UpdateState
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun UpdateBadge(
    state: UpdateState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val visible = state !is UpdateState.Idle && state !is UpdateState.Checking

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(tokens.animFast)) + scaleIn(initialScale = BADGE_ENTER_SCALE),
        exit = fadeOut(tween(tokens.animFast)) + scaleOut(targetScale = BADGE_ENTER_SCALE),
        modifier = modifier,
    ) {
        BadgeContent(state, onClick)
    }
}

@Composable
private fun BadgeContent(state: UpdateState, onClick: () -> Unit) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val targetTint = when (state) {
        is UpdateState.Failed -> colors.destructive
        is UpdateState.ReadyToInstall -> colors.statusOnline
        else -> colors.brandPrimary
    }
    val tint by animateColorAsState(targetTint, tween(tokens.animMedium), label = "updateBadgeTint")

    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.padding(horizontal = tokens.spacingXs),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowCircleUp,
                contentDescription = null,
                tint = tint,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = tokens.spacingSm, end = tokens.spacingSm)
                .size(tokens.indicatorDot)
                .background(tint, CircleShape),
        )
    }
}

private const val BADGE_ENTER_SCALE = 0.85f
