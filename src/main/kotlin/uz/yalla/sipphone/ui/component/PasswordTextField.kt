package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    shape: Shape = LocalAppTokens.current.shapeMedium,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSubtle.copy(alpha = tokens.alphaDisabled),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(tokens.iconDefault),
                    tint = Color.Unspecified,
                )
            }
        },
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
    )
}
