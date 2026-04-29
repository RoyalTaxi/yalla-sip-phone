package uz.yalla.sipphone.feature.auth.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthState
import uz.yalla.sipphone.ui.component.LockPasswordField
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.strings.StringResources
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors
import uz.yalla.sipphone.ui.theme.YallaColors

@Composable
fun AuthScreen(
    state: AuthState,
    loading: Boolean,
    onIntent: (AuthIntent) -> Unit,
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val colors = LocalYallaColors.current

    var pin by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.loginGradientBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(tokens.loginCardWidth)
                .clip(tokens.shapeXl)
                .background(colors.loginCardBackground)
                .padding(horizontal = tokens.loginCardPaddingH, vertical = tokens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacingMd),
        ) {
            BrandBadge()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacingSm),
            ) {
                Text(
                    text = strings.loginTitle,
                    style = TextStyle(
                        fontSize = tokens.textTitle,
                        fontWeight = FontWeight.Bold,
                        color = colors.loginCardTextPrimary,
                    ),
                )
                SubtitleOrError(error = state.error, defaultText = strings.loginSubtitle)
            }

            LockPasswordField(
                value = pin,
                onValueChange = { newValue ->
                    pin = newValue.filter(Char::isDigit)
                    if (state.error != null) onIntent(AuthIntent.ClearError)
                },
                placeholder = strings.loginPasswordPlaceholder,
                enabled = !loading,
                isError = state.error is AuthError.WrongCredentials,
                onSubmit = { onIntent(AuthIntent.Submit(pin)) },
                modifier = Modifier.fillMaxWidth(),
            )

            SubmitButton(
                text = submitButtonText(loading, state.error, strings),
                loading = loading,
                enabled = !loading && pin.isNotBlank(),
                onClick = { onIntent(AuthIntent.Submit(pin)) },
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacingSm),
            ) {
                TextButton(
                    onClick = { onIntent(AuthIntent.OpenManualSheet) },
                    enabled = !loading,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(
                        text = strings.loginManualConnection,
                        color = colors.loginCardTextSecondary,
                        fontSize = tokens.textMd,
                    )
                }
                Text(
                    text = "v${BuildVersion.CURRENT}",
                    color = colors.loginCardBorder,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BrandBadge() {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    Box(
        modifier = Modifier
            .size(tokens.loginBadgeSize)
            .clip(tokens.shapeMedium)
            .background(colors.brandPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Phone,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(tokens.loginBadgeIconSize),
        )
    }
}

@Composable
private fun SubtitleOrError(error: AuthError?, defaultText: String) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    val strings = LocalStrings.current
    Box(
        modifier = Modifier.height(tokens.loginErrorRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (error == null) {
            Text(
                defaultText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.loginCardTextSecondary,
            )
        } else {
            Text(
                error.userMessage(strings),
                style = MaterialTheme.typography.bodySmall,
                color = error.tint(colors),
            )
        }
    }
}

@Composable
private fun SubmitButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.fieldHeightLg)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = tokens.shapeMedium,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandPrimary,
            disabledContainerColor = colors.loginCardSurface,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(tokens.iconDefault),
                strokeWidth = tokens.progressStrokeSmall,
                color = Color.White,
            )
            Spacer(Modifier.width(tokens.spacingSm))
        }
        Text(text, color = Color.White, fontSize = tokens.textLg)
    }
}

private fun submitButtonText(loading: Boolean, error: AuthError?, strings: StringResources): String = when {
    loading -> strings.loginConnecting
    error != null -> strings.loginRetry
    else -> strings.loginButton
}

private fun AuthError.userMessage(strings: StringResources): String = when (this) {
    is AuthError.WrongCredentials -> strings.errorWrongPassword
    AuthError.NoSipAccountsConfigured,
    is AuthError.SipRegistrationTimeout,
    is AuthError.Network -> strings.errorNetworkFailed
}

private fun AuthError.tint(colors: YallaColors): Color = when (this) {
    is AuthError.WrongCredentials -> colors.destructive
    else -> colors.statusWarning
}

private fun YallaColors.loginGradientBrush(): Brush = Brush.linearGradient(
    colors = listOf(loginGradientStart, loginGradientMid, loginGradientEnd),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)
