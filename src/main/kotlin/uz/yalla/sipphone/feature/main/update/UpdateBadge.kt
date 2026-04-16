package uz.yalla.sipphone.feature.main.update

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
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun UpdateBadge(
    state: StateFlow<UpdateState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by state.collectAsState()
    if (current is UpdateState.Idle || current is UpdateState.Checking) return

    val colors = LocalYallaColors.current
    val tint = when (current) {
        is UpdateState.Failed -> colors.destructive
        is UpdateState.ReadyToInstall -> colors.statusOnline
        else -> colors.brandPrimary
    }

    Box(modifier = modifier) {
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
