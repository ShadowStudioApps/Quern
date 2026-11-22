package com.quern.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DebridServiceType(
    val displayName: String,
    val tagline: String,
    val apiDocsHint: String
) {
    TORBOX(
        displayName = "TorBox",
        tagline = "Cloud Torrents, Usenet & Instant HTTPS Cache",
        apiDocsHint = "torbox.app/settings → API Key"
    ),
    REAL_DEBRID(
        displayName = "Real-Debrid",
        tagline = "High-Speed Multi-Hoster & Instant Cached Streams",
        apiDocsHint = "real-debrid.com/apitoken"
    ),
    ALL_DEBRID(
        displayName = "AllDebrid",
        tagline = "Unrestricted Cloud Links & Magnet Unlocking",
        apiDocsHint = "alldebrid.com/apikeys"
    ),
    PREMIUMIZE(
        displayName = "Premiumize",
        tagline = "Cloud Storage, DirectDL & Cached Torrents",
        apiDocsHint = "premiumize.me/account → API Key"
    )
}

@Serializable
enum class MediaServerType(
    val displayName: String,
    val defaultPortHint: String
) {
    JELLYFIN("Jellyfin", "http://192.168.1.10:8096"),
    EMBY("Emby", "http://192.168.1.10:8096"),
    PLEX("Plex", "http://192.168.1.10:32400"),
    WEBDAV("WebDAV / Custom HTTP", "https://your-server.com/webdav/")
}

data class DebridAccountStatus(
    val type: DebridServiceType,
    val isConnected: Boolean,
    val username: String? = null,
    val plan: String? = null,
    val expiresAt: String? = null,
    val error: String? = null
)

data class DebridCloudItem(
    val id: String,
    val name: String,
    val service: DebridServiceType,
    val sizeBytes: Long? = null,
    val status: String = "ready",
    val downloadUrl: String? = null,
    val fileId: String? = null
)

data class MediaServerConfig(
    val id: String,
    val name: String,
    val type: MediaServerType,
    val baseUrl: String,
    val apiKeyOrToken: String = "",
    val username: String = "",
    val password: String = "",
    val userId: String = "",
    val enabled: Boolean = true
)

data class TmdbConfig(
    val apiKey: String = "",
    val language: String = "en-US"
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

data class TraktDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int
)

data class TraktAuthState(
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val username: String? = null
) {
    val isAuthenticated: Boolean get() = accessToken.isNotBlank()
}

enum class UiModeOverride(val label: String) {
    AUTO("Auto (Phone / TV)"),
    TV("Android TV Mode"),
    PHONE("Phone Touch Mode")
}
