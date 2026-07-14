package com.example.aicameraassistant

data class IceServerCredential(
    val urls: List<String>,
    val username: String = "",
    val password: String = "",
    val expiresAtMs: Long = Long.MAX_VALUE
) {
    fun isUsable(nowMs: Long = System.currentTimeMillis()): Boolean =
        urls.isNotEmpty() && expiresAtMs > nowMs + 60_000L
}

fun interface IceServerCredentialProvider {
    suspend fun fetch(): List<IceServerCredential>
}
