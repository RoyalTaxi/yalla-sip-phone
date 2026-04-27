package uz.yalla.sipphone.feature.auth.presentation.model

import com.arkivanov.decompose.ComponentContext
import uz.yalla.sipphone.core.infra.BaseComponent
import uz.yalla.sipphone.domain.auth.usecase.LoginUseCase
import uz.yalla.sipphone.domain.auth.usecase.ManualConnectUseCase
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthEffect
import uz.yalla.sipphone.feature.auth.presentation.intent.AuthState

class AuthComponent(
    componentContext: ComponentContext,
    internal val loginUseCase: LoginUseCase,
    internal val manualConnectUseCase: ManualConnectUseCase,
) : BaseComponent<AuthState, AuthEffect>(componentContext, AuthState.INITIAL)
