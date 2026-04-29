package uz.yalla.sipphone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class YallaColors(

    val brandPrimary: Color,
    val brandPrimaryMuted: Color,
    val brandPrimaryText: Color,
    /** Foreground (text/icon) color rendered on top of [brandPrimary] backgrounds. */
    val onBrandPrimary: Color,
    val brandLight: Color,

    val backgroundBase: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,

    val textBase: Color,
    val textSubtle: Color,

    val borderDefault: Color,
    val borderStrong: Color,

    val errorText: Color,
    val destructive: Color,
    /** Foreground (text/icon) color rendered on top of [destructive] backgrounds. */
    val onDestructive: Color,
    val statusWarning: Color,
    val statusOnline: Color,

    val surfaceMuted: Color,

    val iconSubtle: Color,

    val callReady: Color,
    val callIncoming: Color,
    val callMuted: Color,
    val callOffline: Color,
    val callWrapUp: Color,

    val loginCardBackground: Color,
    val loginCardSurface: Color,
    val loginCardBorder: Color,
    val loginCardTextPrimary: Color,
    val loginCardTextSecondary: Color,

    val loginGradientStart: Color,
    val loginGradientMid: Color,
    val loginGradientEnd: Color,
) {
    companion object {
        val Light = YallaColors(
            brandPrimary = Color(0xFF562DF8),
            brandPrimaryMuted = Color(0xFFC8CBFA),
            brandPrimaryText = Color(0xFF562DF8),
            onBrandPrimary = Color.White,
            brandLight = Color(0xFFC8CBFA),
            backgroundBase = Color(0xFFFFFFFF),
            backgroundSecondary = Color(0xFFF7F7F7),
            backgroundTertiary = Color(0xFFE9EAEA),
            textBase = Color(0xFF101828),

            textSubtle = Color(0xFF5F6B7C),
            borderDefault = Color(0xFFE4E7EC),
            borderStrong = Color(0xFF101828),

            errorText = Color(0xFFC4102C),

            destructive = Color(0xFFF42500),
            onDestructive = Color.White,
            statusWarning = Color(0xFFFF234B),
            statusOnline = Color(0xFF16A34A),
            surfaceMuted = Color(0xFFF7F7F7),
            iconSubtle = Color(0xFF5F6B7C),
            callReady = Color(0xFF2E7D32),
            callIncoming = Color(0xFFD97706),
            callMuted = Color(0xFFF42500),
            callOffline = Color(0xFF6B7280),
            callWrapUp = Color(0xFF7C3AED),

            loginCardBackground = Color(0xFF1A1A20).copy(alpha = 0.88f),
            loginCardSurface = Color(0xFF21222B),
            loginCardBorder = Color(0xFF383843),
            loginCardTextPrimary = Color.White,
            loginCardTextSecondary = Color(0xFF98A2B3),

            loginGradientStart = Color(0xFF7957FF),
            loginGradientMid = Color(0xFF562DF8),
            loginGradientEnd = Color(0xFF3812CE),
        )

        val Dark = YallaColors(
            brandPrimary = Color(0xFF562DF8),
            brandPrimaryMuted = Color(0xFF2C2D34),
            brandPrimaryText = Color(0xFF8B6FFF),
            onBrandPrimary = Color.White,
            brandLight = Color(0xFFC8CBFA),
            backgroundBase = Color(0xFF1A1A20),
            backgroundSecondary = Color(0xFF21222B),
            backgroundTertiary = Color(0xFF2A2B35),
            textBase = Color(0xFFFFFFFF),

            textSubtle = Color(0xFF8B93A3),
            borderDefault = Color(0xFF383843),
            borderStrong = Color(0xFFFFFFFF),
            errorText = Color(0xFFF42500),
            destructive = Color(0xFFF42500),
            onDestructive = Color.White,
            statusWarning = Color(0xFFFF234B),
            statusOnline = Color(0xFF22C55E),
            surfaceMuted = Color(0xFF2C2D34),
            iconSubtle = Color(0xFF98A2B3),
            callReady = Color(0xFF66BB6A),
            callIncoming = Color(0xFFF59E0B),
            callMuted = Color(0xFFF42500),
            callOffline = Color(0xFF98A2B3),
            callWrapUp = Color(0xFF8B5CF6),
            loginCardBackground = Color(0xFF1A1A20).copy(alpha = 0.88f),
            loginCardSurface = Color(0xFF21222B),
            loginCardBorder = Color(0xFF383843),
            loginCardTextPrimary = Color.White,
            loginCardTextSecondary = Color(0xFF98A2B3),

            loginGradientStart = Color(0xFF7957FF),
            loginGradientMid = Color(0xFF562DF8),
            loginGradientEnd = Color(0xFF3812CE),
        )
    }
}

val LocalYallaColors = staticCompositionLocalOf { YallaColors.Light }
