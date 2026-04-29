package uz.yalla.sipphone.feature.auth.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.BuildVersion
import uz.yalla.sipphone.domain.auth.model.AuthError
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthIntent
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthState
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.strings.StringResources
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

private val SplashGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF7957FF), Color(0xFF562DF8), Color(0xFF3812CE)),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

private val CardBg = Color(0xFF1A1A20).copy(alpha = 0.88f)
private val CardTextPrimary = Color.White
private val CardTextSecondary = Color(0xFF98A2B3)
private val CardBorderDefault = Color(0xFF383843)
private val CardSurfaceMuted = Color(0xFF21222B)

@Composable
fun AuthScreen(
    state: AuthState,
    loading: Boolean,
    onIntent: (AuthIntent) -> Unit,
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val colors = LocalYallaColors.current

    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(SplashGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(tokens.shapeXl)
                .background(CardBg)
                .padding(horizontal = 40.dp, vertical = tokens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(tokens.shapeMedium)
                    .background(colors.brandPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.height(tokens.spacingMd))

            Text(
                text = strings.loginTitle,
                style = TextStyle(
                    fontSize = tokens.textTitle,
                    fontWeight = FontWeight.Bold,
                    color = CardTextPrimary,
                ),
            )

            Spacer(Modifier.height(tokens.spacingSm))

            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                state.error?.let { err ->
                    Text(
                        err.toUserMessage(strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = err.errorTint(colors),
                    )
                } ?: Text(
                    strings.loginSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardTextSecondary,
                )
            }

            Spacer(Modifier.height(20.dp))

            val borderColor = if (state.error is AuthError.WrongCredentials) colors.destructive else CardBorderDefault

            BasicTextField(
                value = state.pin,
                onValueChange = { onIntent(AuthIntent.SetPin(it.filter(Char::isDigit))) },
                singleLine = true,
                enabled = !loading,
                textStyle = TextStyle(color = CardTextPrimary, fontSize = tokens.textLg),
                cursorBrush = SolidColor(colors.brandPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(AuthIntent.Submit) }),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tokens.fieldHeightLg)
                            .clip(tokens.shapeMedium)
                            .background(CardSurfaceMuted)
                            .border(tokens.dividerThickness, borderColor, tokens.shapeMedium)
                            .padding(horizontal = tokens.spacingMdSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = CardTextSecondary,
                            modifier = Modifier.size(tokens.iconDefault),
                        )
                        Spacer(Modifier.width(tokens.spacingSm))
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.pin.isEmpty()) {
                                Text(
                                    strings.loginPasswordPlaceholder,
                                    style = TextStyle(fontSize = tokens.textLg, color = CardTextSecondary),
                                )
                            }
                            innerTextField()
                        }
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = CardTextSecondary,
                                modifier = Modifier.size(tokens.iconDefault),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(tokens.spacingMd))

            val buttonText = when {
                loading -> strings.loginConnecting
                state.error != null -> strings.loginRetry
                else -> strings.loginButton
            }

            Button(
                onClick = { onIntent(AuthIntent.Submit) },
                enabled = !loading && state.pin.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.fieldHeightLg)
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = tokens.shapeMedium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandPrimary,
                    disabledContainerColor = CardSurfaceMuted,
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(tokens.iconDefault),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(tokens.spacingSm))
                }
                Text(buttonText, color = Color.White, fontSize = tokens.textLg)
            }

            Spacer(Modifier.height(tokens.spacingMdSm))

            TextButton(
                onClick = { onIntent(AuthIntent.OpenManualSheet) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                enabled = !loading,
            ) {
                Text(strings.loginManualConnection, color = CardTextSecondary, fontSize = tokens.textMd)
            }

            Spacer(Modifier.height(tokens.spacingSm))

            Text(
                "v${BuildVersion.CURRENT}",
                color = CardBorderDefault,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun AuthError.toUserMessage(strings: StringResources): String = when (this) {
    is AuthError.WrongCredentials -> strings.errorWrongPassword
    AuthError.NoSipAccountsConfigured -> strings.errorNetworkFailed
    is AuthError.SipRegistrationTimeout -> strings.errorNetworkFailed
    is AuthError.Network -> strings.errorNetworkFailed
}

@Composable
private fun AuthError.errorTint(colors: uz.yalla.sipphone.ui.theme.YallaColors): Color = when (this) {
    is AuthError.WrongCredentials -> colors.destructive
    else -> colors.statusWarning
}
