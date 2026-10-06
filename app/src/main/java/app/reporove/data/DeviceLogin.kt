package app.reporove.data

import app.reporove.core.network.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
)
@Serializable private data class TokenResponse(@SerialName("access_token") val accessToken: String? = null, val error: String? = null)

class DeviceLogin(private val clientId: String) {
    private val client = OkHttpClient()
    suspend fun start(privateRepositories: Boolean): DeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank())
        val request = Request.Builder().url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(FormBody.Builder().add("client_id", clientId).add("scope", if (privateRepositories) "read:user read:org repo notifications" else "read:user read:org public_repo notifications").build()).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("授权服务暂时不可用")
            AppJson.decodeFromString<DeviceCode>(response.body?.string() ?: throw IOException("授权响应为空"))
        }
    }

    suspend fun awaitToken(code: DeviceCode): String {
        var interval = code.interval.coerceAtLeast(5)
        val deadline = System.nanoTime() + code.expiresIn * 1_000_000_000L
        while (System.nanoTime() < deadline) {
            delay(interval * 1000L)
            val result = withContext(Dispatchers.IO) {
                val request = Request.Builder().url("https://github.com/login/oauth/access_token").header("Accept", "application/json")
                    .post(FormBody.Builder().add("client_id", clientId).add("device_code", code.deviceCode).add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("授权服务暂时不可用")
                    AppJson.decodeFromString<TokenResponse>(response.body?.string() ?: throw IOException("授权响应为空"))
                }
            }
            result.accessToken?.let { return it }
            when (result.error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "access_denied" -> throw IllegalArgumentException("你已取消授权。")
                "expired_token" -> break
                else -> throw IllegalArgumentException("授权未完成，请重新尝试。")
            }
        }
        throw IllegalArgumentException("授权码已过期，请重新登录。")
    }
}
