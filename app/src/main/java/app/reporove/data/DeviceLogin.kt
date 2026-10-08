package app.reporove.data

import app.reporove.core.model.OAuthGrant
import app.reporove.core.model.OAuthMetadata
import app.reporove.core.network.AppJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
)

@Serializable private data class OAuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token_expires_in") val refreshExpiresIn: Long? = null,
    val error: String? = null,
    val interval: Int? = null,
)

/** Fixed GitHub endpoints in production; injectable transport and clocks for protocol tests. */
class DeviceLogin(
    private val clientId: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).build(),
    private val baseUrl: HttpUrl = "https://github.com/".toHttpUrl(),
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    val configured: Boolean get() = clientId.isNotBlank()

    suspend fun start(privateRepositories: Boolean): DeviceCode {
        require(configured) { "此安装包尚未配置浏览器登录，请先使用访问令牌。" }
        val json = post("login/device/code", FormBody.Builder().add("client_id", clientId)
            .add("scope", if (privateRepositories) "read:user read:org repo notifications" else "read:user read:org public_repo notifications").build())
        decode(json).error?.let { throw authError(it) }
        val code = try { AppJson.decodeFromString<DeviceCode>(json) }
        catch (_: Exception) { throw IOException("GitHub 授权响应不完整，请重试。") }
        val uri = runCatching { code.verificationUri.toHttpUrl() }.getOrNull()
        require(uri?.scheme == "https" && uri.host == "github.com" && uri.encodedPath == "/login/device" && uri.username.isEmpty() && uri.password.isEmpty() && uri.port == 443 && uri.query == null && uri.fragment == null) { "GitHub 授权地址无效，请重试。" }
        require(code.deviceCode.isNotBlank() && code.userCode.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")) && code.expiresIn in 1..900 && code.interval in 1..900) { "GitHub 授权码无效，请重试。" }
        return code
    }

    suspend fun awaitToken(code: DeviceCode): OAuthGrant {
        var interval = code.interval.coerceAtLeast(5)
        val deadline = monotonicMillis() + code.expiresIn * 1000L
        while (true) {
            val wait = interval * 1000L
            if (deadline - monotonicMillis() <= wait) break
            pause(wait)
            if (monotonicMillis() >= deadline) break
            val response = decode(post("login/oauth/access_token", FormBody.Builder().add("client_id", clientId)
                .add("device_code", code.deviceCode).add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()))
            response.accessToken?.let { return response.grant(clientId) }
            when (response.error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = maxOf(interval + 5, response.interval ?: 0).coerceAtMost(900)
                "expired_token", "token_expired" -> break
                else -> throw authError(response.error)
            }
        }
        throw authError("expired_token")
    }

    suspend fun refresh(grant: OAuthGrant): OAuthGrant {
        val metadata = grant.metadata
        require(metadata.clientId == clientId && configured) { "登录应用配置已变更，请重新登录 GitHub。" }
        val token = metadata.refreshToken ?: throw authError("bad_refresh_token")
        if (metadata.refreshExpiresAt?.let { now() >= it } == true) throw authError("bad_refresh_token")
        val response = decode(post("login/oauth/access_token", FormBody.Builder().add("client_id", clientId)
            .add("refresh_token", token).add("grant_type", "refresh_token").build()))
        response.error?.let { throw authError(it) }
        return response.grant(clientId)
    }

    private fun OAuthResponse.grant(id: String): OAuthGrant {
        require(!accessToken.isNullOrBlank() && accessToken.length in 10..512 && accessToken.none(Char::isWhitespace) && tokenType.equals("bearer", ignoreCase = true)) { "GitHub 未返回有效的登录凭据，请重新登录。" }
        require(expiresIn == null || (expiresIn in 1..31_536_000 && !refreshToken.isNullOrBlank() && refreshExpiresIn != null && refreshExpiresIn in 1..31_536_000)) { "GitHub 续期凭据不完整，请重新登录。" }
        val receivedAt = now()
        return OAuthGrant(accessToken, OAuthMetadata(id, refreshToken, expiresIn?.let { receivedAt + it * 1000 }, refreshExpiresIn?.let { receivedAt + it * 1000 }))
    }

    private fun decode(json: String): OAuthResponse = try { AppJson.decodeFromString<OAuthResponse>(json) }
    catch (_: Exception) { throw IOException("GitHub 授权响应无法读取，请重试。") }

    private suspend fun post(path: String, body: FormBody): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(baseUrl.resolve(path)!!).header("Accept", "application/json")
            .header("User-Agent", "RepoRove-Android").post(body).build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("无法连接 GitHub 授权服务，请检查网络后重试。", e)) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val json = it.body?.string() ?: throw IOException("GitHub 授权响应为空，请重试。")
                        if (!it.isSuccessful) {
                            val error = runCatching { decode(json).error }.getOrNull()
                            throw error?.let(::authError) ?: IOException("GitHub 授权服务暂时不可用（${it.code}），请稍后重试。")
                        }
                        if (continuation.isActive) continuation.resume(json)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            }
        })
    }

    private fun authError(error: String?): IllegalArgumentException = IllegalArgumentException(when (error) {
        "access_denied" -> "你已在 GitHub 取消授权，可以重新登录。"
        "expired_token", "token_expired" -> "授权码已过期，请重新登录获取新授权码。"
        "device_flow_disabled" -> "RepoRove 的 GitHub 应用尚未开启设备授权，请联系项目维护者，或先使用访问令牌。"
        "incorrect_client_credentials" -> "RepoRove 的 GitHub 应用配置无效，请先使用访问令牌。"
        "bad_refresh_token" -> "GitHub 登录已过期或已被撤销，请重新登录。"
        else -> "GitHub 授权未完成，请重新尝试。"
    })
}
