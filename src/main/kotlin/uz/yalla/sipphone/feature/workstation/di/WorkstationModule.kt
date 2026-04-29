package uz.yalla.sipphone.feature.workstation.di

import org.koin.dsl.module
import uz.yalla.sipphone.data.workstation.bridge.AgentStatusBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.CallEventBridgeEmitter
import uz.yalla.sipphone.data.workstation.bridge.SipConnectionBridgeEmitter
import uz.yalla.sipphone.feature.workstation.sideeffect.CallSideEffects
import uz.yalla.sipphone.feature.workstation.sideeffect.RingtonePlayer

object WorkstationModule {

    private val sideEffectModule = module {
        factory { RingtonePlayer() }
        factory { CallSideEffects(ringtone = get()) }
    }

    private val bridgeModule = module {
        factory { CallEventBridgeEmitter(callEngine = get(), eventEmitter = get()) }
        factory { SipConnectionBridgeEmitter(sipAccountManager = get(), eventEmitter = get()) }
        factory { AgentStatusBridgeEmitter(agentStatusHolder = get(), eventEmitter = get()) }
    }

    val modules = listOf(sideEffectModule, bridgeModule)
}
