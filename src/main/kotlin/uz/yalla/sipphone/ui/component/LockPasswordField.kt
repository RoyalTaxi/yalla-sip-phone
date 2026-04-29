package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

/**
 * Pill-shaped password field with a lock icon prefix and a visibility toggle suffix.
 * Designed for the dark login card; colors are pulled from `loginCard*` slots.
 *
 * `onSubmit` is wired through both [KeyboardActions.onDone] and [Modifier.onPreviewKeyEvent]
 * for [Key.Enter] — Compose Desktop's `onDone` historically didn't fire on hardware Enter on
 * every platform, so the preview-key handler is a belt-and-suspenders fallback.
 */
@Composable
fun LockPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.NumberPassword,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    val strings = LocalStrings.current
    var visible by remember { mutableStateOf(false) }

    val borderColor = if (isError) colors.destructive else colors.loginCardBorder

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(color = colors.loginCardTextPrimary, fontSize = tokens.textLg),
        cursorBrush = SolidColor(colors.brandPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = modifier.onPreviewKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                onSubmit()
                true
            } else {
                false
            }
        },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.fieldHeightLg)
                    .clip(tokens.shapeMedium)
                    .background(colors.loginCardSurface)
                    .border(tokens.dividerThickness, borderColor, tokens.shapeMedium)
                    .padding(horizontal = tokens.spacingMdSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = colors.loginCardTextSecondary,
                    modifier = Modifier.size(tokens.iconDefault),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontSize = tokens.textLg,
                                color = colors.loginCardTextSecondary,
                            ),
                        )
                    }
                    innerTextField()
                }
                IconButton(
                    onClick = { visible = !visible },
                    enabled = enabled,
                    modifier = Modifier.size(tokens.iconButtonSize),
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) strings.accHidePassword else strings.accShowPassword,
                        tint = colors.loginCardTextSecondary,
                        modifier = Modifier.size(tokens.iconDefault),
                    )
                }
            }
        },
    )
}
