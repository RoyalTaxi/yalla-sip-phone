package uz.yalla.sipphone.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import uz.yalla.sipphone.data.settings.AppSettings

val settingsModule = module {
    singleOf(::AppSettings)
}
