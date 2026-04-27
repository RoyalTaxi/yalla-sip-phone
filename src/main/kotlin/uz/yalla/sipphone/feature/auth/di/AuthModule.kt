package uz.yalla.sipphone.feature.auth.di

import com.arkivanov.decompose.ComponentContext
import org.koin.dsl.module
import uz.yalla.sipphone.feature.auth.presentation.model.AuthComponent

object AuthModule {
    private val componentModule = module {
        factory { (componentContext: ComponentContext) ->
            AuthComponent(
                componentContext = componentContext,
                loginUseCase = get(),
                manualConnectUseCase = get(),
            )
        }
    }
    val modules = listOf(componentModule)
}
