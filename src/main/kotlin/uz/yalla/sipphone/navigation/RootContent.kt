package uz.yalla.sipphone.navigation

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import uz.yalla.sipphone.feature.login.LoginScreen
import uz.yalla.sipphone.feature.main.MainScreen
import uz.yalla.sipphone.ui.theme.LocalAppTokens

@Composable
fun RootContent(
    root: RootComponent,
    isDarkTheme: Boolean,
    locale: String,
    onThemeToggle: () -> Unit,
    onLocaleChange: (String) -> Unit,
) {
    val tokens = LocalAppTokens.current
    val childStack by root.childStack.subscribeAsState()

    // Desktop navigation = cross-fade only. Screen transitions on desktop aren't a "page
    // stack" — they're a state swap (unauthenticated → authenticated). Sliding would feel
    // like iOS push/pop, which is wrong for a native softphone app. A short fade reads as
    // "the UI updated" rather than "I navigated."
    Children(
        stack = childStack,
        animation = stackAnimation(
            fade(animationSpec = tween(tokens.animFast, easing = LinearOutSlowInEasing)),
        ),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Login ->
                LoginScreen(instance.component)
            is RootComponent.Child.Main ->
                MainScreen(
                    component = instance.component,
                    isDarkTheme = isDarkTheme,
                    locale = locale,
                    onThemeToggle = onThemeToggle,
                    onLocaleChange = onLocaleChange,
                )
        }
    }
}
