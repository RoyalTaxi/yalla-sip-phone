package uz.yalla.sipphone.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.RegistrationState
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalExtendedColors

@Composable
fun ConnectionStatusCard(state: RegistrationState, modifier: Modifier = Modifier) {
    val tokens = LocalAppTokens.current
    val extendedColors = LocalExtendedColors.current

    AnimatedVisibility(
        visible = state is RegistrationState.Registering || state is RegistrationState.Failed,
        enter = fadeIn(tween(tokens.animMedium)) + slideInVertically(
            initialOffsetY = { it / 4 }, animationSpec = tween(tokens.animMedium),
        ),
        exit = fadeOut(tween(tokens.animFast)) + shrinkVertically(tween(tokens.animFast)),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val containerColor by animateColorAsState(
            targetValue = when (state) {
                is RegistrationState.Registering -> MaterialTheme.colorScheme.secondaryContainer
                is RegistrationState.Registered -> extendedColors.successContainer
                is RegistrationState.Failed -> MaterialTheme.colorScheme.errorContainer
                is RegistrationState.Idle -> Color.Transparent
            }, animationSpec = tween(tokens.animMedium),
        )
        val contentColor by animateColorAsState(
            targetValue = when (state) {
                is RegistrationState.Registering -> MaterialTheme.colorScheme.onSecondaryContainer
                is RegistrationState.Registered -> extendedColors.onSuccessContainer
                is RegistrationState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                is RegistrationState.Idle -> Color.Transparent
            }, animationSpec = tween(tokens.animMedium),
        )

        Card(colors = CardDefaults.cardColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(tokens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is RegistrationState.Registering -> CircularProgressIndicator(
                        modifier = Modifier.size(tokens.iconMedium), strokeWidth = 2.5.dp, color = contentColor,
                    )
                    is RegistrationState.Registered -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = Strings.REG_STATUS_REGISTERED,
                        tint = contentColor,
                    )
                    is RegistrationState.Failed -> Icon(
                        Icons.Filled.Error,
                        contentDescription = Strings.REG_STATUS_FAILED,
                        tint = contentColor,
                    )
                    is RegistrationState.Idle -> {}
                }
                Column {
                    Text(
                        text = when (state) {
                            is RegistrationState.Registering -> Strings.REG_STATUS_REGISTERING
                            is RegistrationState.Registered -> Strings.REG_STATUS_REGISTERED
                            is RegistrationState.Failed -> Strings.REG_STATUS_FAILED
                            is RegistrationState.Idle -> ""
                        },
                        style = MaterialTheme.typography.titleSmall, color = contentColor,
                    )
                    Text(
                        text = when (state) {
                            is RegistrationState.Registering -> Strings.REG_DETAIL_CONNECTING
                            is RegistrationState.Registered -> state.server
                            is RegistrationState.Failed -> state.error.displayMessage
                            is RegistrationState.Idle -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
