package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun SplashScreen() {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(colors.loginGradientStart, colors.loginGradientEnd),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.splashStackSpacing),
        ) {
            Text(
                text = strings.appTitle,
                color = colors.onBrandPrimary,
                fontSize = tokens.splashLogoFontSize,
                fontWeight = FontWeight.SemiBold,
            )
            CircularProgressIndicator(
                modifier = Modifier.size(tokens.splashSpinnerSize),
                color = colors.onBrandPrimary,
                strokeWidth = tokens.splashSpinnerStroke,
            )
        }
    }
}
