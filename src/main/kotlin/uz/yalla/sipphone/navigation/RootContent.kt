package uz.yalla.sipphone.navigation

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import uz.yalla.sipphone.feature.auth.presentation.view.AuthRoute
import uz.yalla.sipphone.feature.auth.presentation.view.FromAuth
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

    Children(
        stack = childStack,
        animation = stackAnimation(
            fade(animationSpec = tween(tokens.animFast, easing = LinearOutSlowInEasing)),
        ),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Auth ->
                AuthRoute(
                    component = instance.component,
                    navigateTo = { from ->
                        when (from) {
                            is FromAuth.ToWorkstation -> root.onAuthSuccess(from.session)
                        }
                    },
                )
            is RootComponent.Child.Workstation ->
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
