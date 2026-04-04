package uz.yalla.sipphone.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.WindowState
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import uz.yalla.sipphone.feature.dialer.DialerScreen
import uz.yalla.sipphone.feature.registration.RegistrationScreen
import uz.yalla.sipphone.ui.theme.LocalAppTokens

@Composable
fun RootContent(root: RootComponent, windowState: WindowState) {
    val tokens = LocalAppTokens.current
    val childStack by root.childStack.subscribeAsState()

    LaunchedEffect(childStack.active.instance) {
        val targetSize = when (childStack.active.instance) {
            is RootComponent.Child.Registration -> tokens.registrationWindowSize
            is RootComponent.Child.Dialer -> tokens.dialerWindowSize
        }
        windowState.size = targetSize
    }

    Children(
        stack = childStack,
        animation = stackAnimation {
            slide(animationSpec = tween(tokens.animSlow, easing = FastOutSlowInEasing)) +
                fade(animationSpec = tween(tokens.animFast))
        },
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Registration ->
                RegistrationScreen(instance.component)
            is RootComponent.Child.Dialer ->
                DialerScreen(instance.component)
        }
    }
}
