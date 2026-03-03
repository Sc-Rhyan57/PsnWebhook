package com.psnwebhook.psn

import com.psnwebhook.model.PsnAccount
import com.psnwebhook.model.PsnGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PsnApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchProfile(accessToken: String): PsnAccount = withContext(Dispatchers.IO) {
        val resp = http.newCall(
            Request.Builder()
                .url("https://us-prof.np.community.playstation.net/userProfile/v1/users/me/profile2?fields=onlineId,accountId,avatarUrls,trophySummary,presences")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                .get()
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty profile response")
        if (!resp.isSuccessful) throw Exception("Profile failed ${resp.code}: $text")

        val json = JSONObject(text)
        val profile = json.optJSONObject("profile") ?: json

        val onlineId = profile.optString("onlineId", "Unknown")
        val accountId = profile.optString("accountId", "")

        val avatarUrls = profile.optJSONArray("avatarUrls")
        val avatarUrl = if (avatarUrls != null && avatarUrls.length() > 0) {
            avatarUrls.getJSONObject(avatarUrls.length() - 1).optString("avatarUrl", "")
        } else ""

        val trophySummary = profile.optJSONObject("trophySummary")
        val level = trophySummary?.optInt("level", 1) ?: 1

        val presences = profile.optJSONArray("presences")
        val presenceState = if (presences != null && presences.length() > 0) {
            presences.getJSONObject(0).optString("onlineStatus", "offline")
        } else "offline"

        PsnAccount(
            onlineId = onlineId,
            accountId = accountId,
            avatarUrl = avatarUrl,
            level = level,
            presenceState = presenceState
        )
    }

    suspend fun fetchRecentGames(accessToken: String, accountId: String): List<PsnGame> = withContext(Dispatchers.IO) {
        try {
            val id = if (accountId.isEmpty()) "me" else accountId
            val resp = http.newCall(
                Request.Builder()
                    .url("https://m.np.playstation.com/api/gamelist/v2/users/$id/titles?categories=ps4_game,ps5_native_game&limit=20&offset=0")
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                    .get()
                    .build()
            ).execute()

            val text = resp.body?.string() ?: return@withContext emptyList()
            if (!resp.isSuccessful) return@withContext emptyList()

            val json = JSONObject(text)
            val titles = json.optJSONArray("titles") ?: return@withContext emptyList()

            val games = mutableListOf<PsnGame>()
            for (i in 0 until titles.length()) {
                val t = titles.getJSONObject(i)
                val titleId = t.optString("titleId", "")
                if (titleId.isEmpty()) continue

                val name = t.optString("name", titleId)
                val imageUrlObj = t.optJSONObject("imageUrl") ?: t.optJSONObject("image")
                val imageUrl = imageUrlObj?.optString("url", "") ?: t.optString("imageUrl", "")
                val category = t.optString("category", "")
                val platform = when {
                    category.contains("ps5", true) -> "PS5"
                    category.contains("ps4", true) -> "PS4"
                    else -> "PS5"
                }
                val lastPlayed = t.optString("lastPlayedDateTime", "")
                games.add(PsnGame(titleId = titleId, name = name, imageUrl = imageUrl, platform = platform, lastPlayedAt = lastPlayed))
            }
            games
        } catch (_: Exception) { emptyList() }
    }

    suspend fun setPresence(accessToken: String, titleId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = """{"titleId":"$titleId","titleType":""}"""
            val body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                payload
            )
            val resp = http.newCall(
                Request.Builder()
                    .url("https://m.np.playstation.com/api/sessionManager/v1/remotePlay/sessionEvent")
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
            ).execute()
            resp.isSuccessful || resp.code == 204
        } catch (_: Exception) { false }
    }

    suspend fun fetchTrophySummary(accessToken: String): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        try {
            val resp = http.newCall(
                Request.Builder()
                    .url("https://m.np.playstation.com/api/trophy/v1/users/me/trophySummary")
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                    .get()
                    .build()
            ).execute()

            val text = resp.body?.string() ?: return@withContext Triple(0, 0, 0)
            if (!resp.isSuccessful) return@withContext Triple(0, 0, 0)

            val json = JSONObject(text)
            val earned = json.optJSONObject("earnedTrophies")
            val gold = earned?.optInt("gold", 0) ?: 0
            val silver = earned?.optInt("silver", 0) ?: 0
            val bronze = earned?.optInt("bronze", 0) ?: 0
            Triple(gold, silver, bronze)
        } catch (_: Exception) { Triple(0, 0, 0) }
    }
}
