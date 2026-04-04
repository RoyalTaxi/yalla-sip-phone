package uz.yalla.sipphone.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import uz.yalla.sipphone.data.pjsip.PjsipBridge
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.RegistrationEngine

val appModule = module {
    singleOf(::PjsipBridge) bind RegistrationEngine::class
    singleOf(::AppSettings)
}
