package uz.yalla.sipphone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppTokens(

    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMdSm: Dp = 12.dp,
    val spacingMd: Dp = 16.dp,
    val spacingLg: Dp = 24.dp,
    val spacingXl: Dp = 32.dp,

    val elevationNone: Dp = 0.dp,
    val elevationLow: Dp = 2.dp,
    val elevationMedium: Dp = 6.dp,

    val cornerXs: Dp = 6.dp,
    val cornerSmall: Dp = 8.dp,
    val cornerMedium: Dp = 10.dp,
    val cornerLarge: Dp = 14.dp,
    val cornerXl: Dp = 16.dp,

    val shapeXs: Shape = RoundedCornerShape(cornerXs),
    val shapeSmall: Shape = RoundedCornerShape(cornerSmall),
    val shapeMedium: Shape = RoundedCornerShape(cornerMedium),
    val shapeLarge: Shape = RoundedCornerShape(cornerLarge),
    val shapeXl: Shape = RoundedCornerShape(cornerXl),

    val alphaSubtle: Float = 0.1f,
    val alphaMuted: Float = 0.12f,
    val alphaLight: Float = 0.15f,
    val alphaBorder: Float = 0.25f,
    val alphaMedium: Float = 0.3f,
    val alphaFocus: Float = 0.5f,
    val alphaDisabled: Float = 0.6f,
    val alphaHint: Float = 0.7f,

    val textXs: TextUnit = 10.sp,
    val textSm: TextUnit = 11.sp,
    val textBase: TextUnit = 12.sp,
    val textMd: TextUnit = 13.sp,
    val textLg: TextUnit = 14.sp,
    val textXl: TextUnit = 16.sp,
    val textTitle: TextUnit = 20.sp,

    val windowMinWidth: Dp = 380.dp,
    val windowMinHeight: Dp = 180.dp,

    val iconSmall: Dp = 16.dp,
    val iconDefault: Dp = 18.dp,
    val iconMedium: Dp = 24.dp,

    val indicatorDot: Dp = 8.dp,
    val indicatorDotLarge: Dp = 10.dp,
    val dividerThickness: Dp = 1.dp,
    val dividerHeight: Dp = 32.dp,

    val chipHeight: Dp = 28.dp,
    val iconButtonSize: Dp = 36.dp,
    val iconButtonSizeLarge: Dp = 40.dp,
    val fieldHeight: Dp = 36.dp,
    val fieldHeightLg: Dp = 44.dp,
    val segmentButtonSize: Dp = 32.dp,

    val toolbarHeight: Dp = 52.dp,
    val toolbarPaddingH: Dp = 12.dp,
    val toolbarZoneGap: Dp = 8.dp,

    val dropdownItemMinHeight: Dp = 36.dp,
    val dropdownWidth: Dp = 180.dp,

    val settingsDialogWidth: Dp = 340.dp,
    val settingsDialogHeight: Dp = 356.dp,
    val settingsCardWidth: Dp = 320.dp,

    val loginCardWidth: Dp = 320.dp,
    val loginCardPaddingH: Dp = 40.dp,
    val loginBadgeSize: Dp = 56.dp,
    val loginBadgeIconSize: Dp = 28.dp,
    val loginErrorRowHeight: Dp = 20.dp,
    val loginManualPortFieldWidth: Dp = 90.dp,
    val loginManualAccountListMaxHeight: Dp = 120.dp,
    val loginManualRemoveIconSize: Dp = 20.dp,

    val updateDialogMaxHeight: Dp = 400.dp,
    val updateDiagnosticsMaxHeight: Dp = 500.dp,

    val splashStackSpacing: Dp = 20.dp,
    val splashLogoFontSize: TextUnit = 32.sp,
    val splashSpinnerSize: Dp = 36.dp,
    val splashSpinnerStroke: Dp = 3.dp,

    val progressSmall: Dp = 18.dp,
    val progressStrokeSmall: Dp = 2.dp,

    val animFast: Int = 120,
    val animMedium: Int = 180,
    val animSlow: Int = 220,

    val loginWindowSize: DpSize = DpSize(1280.dp, 720.dp),
    val mainWindowSize: DpSize = DpSize(1280.dp, 720.dp),
) {
    fun minimumAwtDimension(): java.awt.Dimension =
        java.awt.Dimension(windowMinWidth.value.toInt(), windowMinHeight.value.toInt())
}

val LocalAppTokens = staticCompositionLocalOf { AppTokens() }
