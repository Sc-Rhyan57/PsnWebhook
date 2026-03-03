package com.psnwebhook.model

import kotlinx.serialization.Serializable

@Serializable
data class PsnAccount(
    val onlineId: String = "",
    val accountId: String = "",
    val avatarUrl: String = "",
    val npsso: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val level: Int = 0,
    val presenceState: String = "offline"
)

@Serializable
data class PsnGame(
    val titleId: String,
    val name: String,
    val imageUrl: String = "",
    val platform: String = "PS5",
    val lastPlayedAt: String = ""
)

@Serializable
data class PresenceConfig(
    val titleId: String = "",
    val titleName: String = "",
    val isActive: Boolean = false,
    val startedAt: Long = 0L
)

enum class AppScreen {
    LOGIN, HOME
}
