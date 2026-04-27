package uz.yalla.sipphone.data.jcef.bridge

import kotlinx.serialization.json.Json

internal val bridgeJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
