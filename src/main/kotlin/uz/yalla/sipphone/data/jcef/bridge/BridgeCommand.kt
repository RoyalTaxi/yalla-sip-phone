package uz.yalla.sipphone.data.jcef.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BridgeCommand(
    val command: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class CommandResult(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: CommandError? = null,
) {
    companion object {
        fun success(data: JsonElement? = null) = CommandResult(success = true, data = data)
        fun error(code: String, message: String, recoverable: Boolean) = CommandResult(
            success = false,
            error = CommandError(code, message, recoverable),
        )
    }
}

@Serializable
data class CommandError(
    val code: String,
    val message: String,
    val recoverable: Boolean,
)
