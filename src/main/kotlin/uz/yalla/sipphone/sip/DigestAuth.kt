package uz.yalla.sipphone.sip

import java.security.MessageDigest

data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val algorithm: String = "MD5",
    val qop: String? = null,
    val opaque: String? = null
)

object DigestAuth {

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun computeResponse(
        username: String, realm: String, password: String,
        nonce: String, method: String, uri: String,
        qop: String? = null, nc: String? = null, cnonce: String? = null
    ): String {
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        return if (qop == "auth" && nc != null && cnonce != null) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }
    }

    fun parseChallenge(wwwAuthenticate: String): DigestChallenge {
        val body = wwwAuthenticate.removePrefix("Digest ").trim()
        val params = mutableMapOf<String, String>()
        var remaining = body
        while (remaining.isNotBlank()) {
            val eqIndex = remaining.indexOf('=')
            if (eqIndex < 0) break
            val key = remaining.substring(0, eqIndex).trim()
            remaining = remaining.substring(eqIndex + 1).trim()
            val value: String
            if (remaining.startsWith("\"")) {
                val closeQuote = remaining.indexOf('"', 1)
                value = remaining.substring(1, closeQuote)
                remaining = remaining.substring(closeQuote + 1).trimStart(',').trim()
            } else {
                val commaIndex = remaining.indexOf(',')
                if (commaIndex >= 0) {
                    value = remaining.substring(0, commaIndex).trim()
                    remaining = remaining.substring(commaIndex + 1).trim()
                } else {
                    value = remaining.trim()
                    remaining = ""
                }
            }
            params[key] = value
        }
        return DigestChallenge(
            realm = params["realm"] ?: error("Missing realm in WWW-Authenticate"),
            nonce = params["nonce"] ?: error("Missing nonce in WWW-Authenticate"),
            algorithm = params["algorithm"] ?: "MD5",
            qop = params["qop"],
            opaque = params["opaque"]
        )
    }

    fun buildAuthorizationHeader(
        username: String, challenge: DigestChallenge,
        method: String, uri: String, response: String,
        nc: String? = null, cnonce: String? = null
    ): String = buildString {
        append("Digest username=\"$username\"")
        append(", realm=\"${challenge.realm}\"")
        append(", nonce=\"${challenge.nonce}\"")
        append(", uri=\"$uri\"")
        append(", response=\"$response\"")
        append(", algorithm=${challenge.algorithm}")
        challenge.opaque?.let { append(", opaque=\"$it\"") }
        if (challenge.qop != null && nc != null && cnonce != null) {
            append(", qop=${challenge.qop}")
            append(", nc=$nc")
            append(", cnonce=\"$cnonce\"")
        }
    }
}
