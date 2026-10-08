package app.reporove.core.model

import kotlinx.serialization.Serializable

/** Stored only inside the Keystore-encrypted account record; never written to logs. */
@Serializable data class OAuthMetadata(
    val clientId: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val refreshExpiresAt: Long? = null,
)

data class OAuthGrant(val accessToken: String, val metadata: OAuthMetadata)
