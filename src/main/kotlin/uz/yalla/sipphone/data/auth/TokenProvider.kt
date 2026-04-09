package uz.yalla.sipphone.data.auth

interface TokenProvider {
    suspend fun getToken(): String?
    suspend fun setToken(token: String)
    suspend fun clearToken()
}

class InMemoryTokenProvider : TokenProvider {
    @Volatile
    private var token: String? = null

    override suspend fun getToken(): String? = token
    override suspend fun setToken(token: String) { this.token = token }
    override suspend fun clearToken() { this.token = null }
}
