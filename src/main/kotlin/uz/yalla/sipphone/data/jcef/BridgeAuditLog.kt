package uz.yalla.sipphone.data.jcef

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.util.PhoneNumberMasker

private val logger = KotlinLogging.logger {}

class BridgeAuditLog {
    private val phoneParamKeys = setOf("number", "phone", "callerNumber")

    fun logCommand(command: String, params: Map<String, String>, result: String) {
        val masked = formatEntry(command, params)
        logger.debug { "BRIDGE CMD: $masked → $result" }
    }

    fun logEvent(eventName: String, payloadJson: String) {
        logger.debug { "BRIDGE EVT: $eventName" }
    }

    fun formatEntry(command: String, params: Map<String, String>): String {
        val maskedParams = params.mapValues { (key, value) ->
            if (key in phoneParamKeys) PhoneNumberMasker.mask(value) else value
        }
        return "$command($maskedParams)"
    }
}
