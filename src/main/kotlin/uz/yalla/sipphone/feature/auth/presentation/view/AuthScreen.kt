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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthState
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun AuthScreen(
    state: AuthState,
    loading: Boolean,
    onIntent: (AuthIntent) -> Unit,
) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    val backgroundGradient = Brush.linearGradient(
        colors = listOf(colors.brandPrimary, colors.brandPrimaryMuted, colors.brandPrimary),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(tokens.cornerXl))
                .background(colors.backgroundBase)
                .padding(tokens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.loginTitle,
                fontSize = tokens.textTitle,
                fontWeight = FontWeight.SemiBold,
                color = colors.textBase,
            )
            Spacer(Modifier.height(tokens.spacingSm))
            Text(
                text = strings.loginSubtitle,
                fontSize = tokens.textBase,
                color = colors.textSubtle,
            )
            Spacer(Modifier.height(tokens.spacingLg))

            OutlinedTextField(
                value = state.pin,
                onValueChange = { onIntent(AuthIntent.SetPin(it.filter(Char::isDigit))) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                placeholder = { Text(strings.loginPasswordPlaceholder) },
                enabled = !loading,
                isError = state.error != null,
            )

            state.error?.let { err ->
                Spacer(Modifier.height(tokens.spacingSm))
                Text(
                    text = err.toUserMessage(strings),
                    color = colors.errorText,
                    fontSize = tokens.textSm,
                )
            }

            Spacer(Modifier.height(tokens.spacingLg))

            Button(
                onClick = { onIntent(AuthIntent.Submit) },
                enabled = !loading && state.pinReady(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.brandPrimaryText,
                    )
                } else {
                    Text(strings.loginButton)
                }
            }

            Spacer(Modifier.height(tokens.spacingSm))
            TextButton(
                onClick = { onIntent(AuthIntent.OpenManualSheet) },
                enabled = !loading,
            ) {
                Text(strings.loginManualConnection, color = colors.brandPrimary)
            }
        }
    }
}

private fun AuthError.toUserMessage(strings: uz.yalla.sipphone.ui.strings.StringResources): String =
    when (this) {
        is AuthError.WrongCredentials -> strings.errorWrongPassword
        AuthError.NoSipAccountsConfigured -> strings.errorNetworkFailed
        is AuthError.SipRegistrationTimeout -> strings.errorNetworkFailed
        is AuthError.Network -> strings.errorNetworkFailed
    }
