package uz.yalla.sipphone.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import uz.yalla.sipphone.feature.dialer.DialerScreen
import uz.yalla.sipphone.feature.registration.RegistrationScreen

@Composable
fun RootContent(root: RootComponent) {
    val childStack by root.childStack.subscribeAsState()

    Children(
        stack = childStack,
        animation = stackAnimation {
            slide(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                fade(animationSpec = tween(250))
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
