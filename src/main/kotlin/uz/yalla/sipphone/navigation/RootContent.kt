package uz.yalla.sipphone.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import uz.yalla.sipphone.feature.dialer.DialerScreen
import uz.yalla.sipphone.feature.registration.RegistrationScreen

private val RegistrationSize = DpSize(420.dp, 520.dp)
private val DialerSize = DpSize(800.dp, 180.dp)

@Composable
fun RootContent(root: RootComponent, windowState: WindowState) {
    val childStack by root.childStack.subscribeAsState()

    // Resize window based on active screen
    LaunchedEffect(childStack.active.instance) {
        val targetSize = when (childStack.active.instance) {
            is RootComponent.Child.Registration -> RegistrationSize
            is RootComponent.Child.Dialer -> DialerSize
        }
        windowState.size = targetSize
    }

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
