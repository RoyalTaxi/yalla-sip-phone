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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import uz.yalla.sipphone.domain.update.UpdateState
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun UpdateBadge(
    state: StateFlow<UpdateState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by state.collectAsState()
    val visible = current !is UpdateState.Idle && current !is UpdateState.Checking

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(LocalAppTokens.current.animFast)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(LocalAppTokens.current.animFast)) + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        val colors = LocalYallaColors.current
        val tokens = LocalAppTokens.current
        val targetTint = when (current) {
            is UpdateState.Failed -> colors.destructive
            is UpdateState.ReadyToInstall -> colors.statusOnline
            else -> colors.brandPrimary
        }
        val tint by animateColorAsState(targetTint, tween(tokens.animMedium), label = "updateBadgeTint")

        Box {
            IconButton(
                onClick = onClick,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowCircleUp,
                    contentDescription = "Update",
                    tint = tint,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .background(tint, CircleShape),
            )
        }
    }
}
