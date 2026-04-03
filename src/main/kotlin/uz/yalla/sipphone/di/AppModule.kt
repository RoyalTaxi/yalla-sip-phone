package uz.yalla.sipphone.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import uz.yalla.sipphone.data.pjsip.PjsipBridge
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.domain.SipEngine

val appModule = module {
    // SipEngine interface -> PjsipBridge implementation
    singleOf(::PjsipBridge) bind SipEngine::class

    // Settings
    singleOf(::AppSettings)
}
