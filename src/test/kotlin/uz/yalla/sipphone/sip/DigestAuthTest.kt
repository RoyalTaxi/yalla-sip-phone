package uz.yalla.sipphone.sip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DigestAuthTest {

    @Test
    fun `md5Hex produces correct hash`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", DigestAuth.md5Hex(""))
        assertEquals("5d41402abc4b2a76b9719d911017c592", DigestAuth.md5Hex("hello"))
    }

    @Test
    fun `computeResponse without qop`() {
        val ha1 = DigestAuth.md5Hex("102:oktell:1234qwerQQ")
        val ha2 = DigestAuth.md5Hex("REGISTER:sip:192.168.0.22")
        val expected = DigestAuth.md5Hex("$ha1:testnonce123:$ha2")

        val result = DigestAuth.computeResponse(
            username = "102", realm = "oktell", password = "1234qwerQQ",
            nonce = "testnonce123", method = "REGISTER", uri = "sip:192.168.0.22"
        )
        assertEquals(expected, result)
    }

    @Test
    fun `computeResponse with qop auth`() {
        val ha1 = DigestAuth.md5Hex("102:oktell:1234qwerQQ")
        val ha2 = DigestAuth.md5Hex("REGISTER:sip:192.168.0.22")
        val expected = DigestAuth.md5Hex("$ha1:testnonce123:00000001:abc123:auth:$ha2")

        val result = DigestAuth.computeResponse(
            username = "102", realm = "oktell", password = "1234qwerQQ",
            nonce = "testnonce123", method = "REGISTER", uri = "sip:192.168.0.22",
            qop = "auth", nc = "00000001", cnonce = "abc123"
        )
        assertEquals(expected, result)
    }

    @Test
    fun `parseChallenge extracts all fields`() {
        val header = """Digest realm="oktell.local", nonce="abc123def", algorithm=MD5, qop="auth", opaque="xyz789""""
        val challenge = DigestAuth.parseChallenge(header)
        assertEquals("oktell.local", challenge.realm)
        assertEquals("abc123def", challenge.nonce)
        assertEquals("MD5", challenge.algorithm)
        assertEquals("auth", challenge.qop)
        assertEquals("xyz789", challenge.opaque)
    }

    @Test
    fun `parseChallenge handles minimal challenge`() {
        val header = """Digest realm="192.168.0.22", nonce="abcdef""""
        val challenge = DigestAuth.parseChallenge(header)
        assertEquals("192.168.0.22", challenge.realm)
        assertEquals("abcdef", challenge.nonce)
        assertEquals("MD5", challenge.algorithm)
        assertEquals(null, challenge.qop)
        assertEquals(null, challenge.opaque)
    }

    @Test
    fun `buildAuthorizationHeader formats correctly`() {
        val challenge = DigestChallenge(realm = "oktell", nonce = "testnonce", qop = "auth", opaque = "testopaque")
        val header = DigestAuth.buildAuthorizationHeader(
            username = "102", challenge = challenge, method = "REGISTER",
            uri = "sip:192.168.0.22", response = "abcdef123456",
            nc = "00000001", cnonce = "mycnonce"
        )
        assert(header.startsWith("Digest "))
        assert("username=\"102\"" in header)
        assert("realm=\"oktell\"" in header)
        assert("nonce=\"testnonce\"" in header)
        assert("uri=\"sip:192.168.0.22\"" in header)
        assert("response=\"abcdef123456\"" in header)
        assert("algorithm=MD5" in header)
        assert("qop=auth" in header)
        assert("nc=00000001" in header)
        assert("cnonce=\"mycnonce\"" in header)
        assert("opaque=\"testopaque\"" in header)
    }

    @Test
    fun `buildAuthorizationHeader without qop`() {
        val challenge = DigestChallenge(realm = "oktell", nonce = "testnonce")
        val header = DigestAuth.buildAuthorizationHeader(
            username = "102", challenge = challenge, method = "REGISTER",
            uri = "sip:192.168.0.22", response = "abcdef123456"
        )
        assert("qop" !in header)
        assert(", nc=" !in header)
        assert("cnonce" !in header)
    }
}
