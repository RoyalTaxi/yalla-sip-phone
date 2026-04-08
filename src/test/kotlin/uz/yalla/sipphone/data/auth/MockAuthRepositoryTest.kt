package uz.yalla.sipphone.data.auth

import kotlinx.coroutines.test.runTest
import uz.yalla.sipphone.domain.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockAuthRepositoryTest {
    private val repo: AuthRepository = MockAuthRepository()

    @Test
    fun `correct pin returns success`() = runTest {
        val result = repo.login("test123")
        assertTrue(result.isSuccess)
        val auth = result.getOrThrow()
        assertNotNull(auth.token)
        assertTrue(auth.token.isNotEmpty())
        assertEquals("103", auth.sipCredentials.username)
        assertEquals("192.168.30.103", auth.sipCredentials.server)
        assertEquals(5060, auth.sipCredentials.port)
        assertEquals("UDP", auth.sipCredentials.transport)
        assertEquals("Islom", auth.agent.name)
        assertEquals("agent-042", auth.agent.id)
        assertTrue(auth.dispatcherUrl.isNotEmpty())
    }

    @Test
    fun `wrong pin returns failure`() = runTest {
        val result = repo.login("wrong")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Invalid PIN", ex.message)
    }

    @Test
    fun `empty pin returns failure`() = runTest {
        val result = repo.login("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `logout returns success`() = runTest {
        val result = repo.logout()
        assertTrue(result.isSuccess)
    }
}
