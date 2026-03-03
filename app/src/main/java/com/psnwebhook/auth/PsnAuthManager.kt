package com.psnwebhook.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PsnAuthManager {

    private const val TAG = "PsnAuth"

    private const val CLIENT_ID = "09515159-7237-4370-9b40-3806e67c0891"
    private const val SCOPE = "psn:mobile.v2.core psn:clientapp"
    private const val REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect"
    private const val OAUTH_CODE_URL = "https://ca.account.sony.com/api/authz/v3/oauth/authorize"
    private const val OAUTH_TOKEN_URL = "https://ca.account.sony.com/api/authz/v3/oauth/token"
    private const val BASIC_AUTH = "MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVzZXJfY2xpZW50aWQ="

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun exchangeNpssoForTokens(npsso: String): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val codeUrl = buildString {
            append(OAUTH_CODE_URL)
            append("?access_type=offline")
            append("&client_id=$CLIENT_ID")
            append("&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
            append("&response_type=code")
            append("&scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}")
        }

        val codeResp = http.newCall(
            Request.Builder()
                .url(codeUrl)
                .header("Cookie", "npsso=$npsso")
                .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                .get()
                .build()
        ).execute()

        val location = codeResp.header("Location") ?: throw Exception("No redirect location from PSN auth")
        Log.d(TAG, "Redirect location: $location")

        val code = if (location.contains("code=")) {
            location.substringAfter("code=").substringBefore("&")
        } else {
            throw Exception("No auth code in redirect: $location")
        }

        val tokenBody = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .add("token_format", "jwt")
            .build()

        val tokenResp = http.newCall(
            Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .header("Authorization", "Basic $BASIC_AUTH")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                .post(tokenBody)
                .build()
        ).execute()

        val tokenText = tokenResp.body?.string() ?: throw Exception("Empty token response")
        if (!tokenResp.isSuccessful) throw Exception("Token exchange failed ${tokenResp.code}: $tokenText")

        val json = JSONObject(tokenText)
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

        Triple(accessToken, refreshToken, expiresAt)
    }

    suspend fun refreshAccessToken(refreshToken: String): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_uri", REDIRECT_URI)
            .add("token_format", "jwt")
            .build()

        val resp = http.newCall(
            Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .header("Authorization", "Basic $BASIC_AUTH")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT")
                .post(body)
                .build()
        ).execute()

        val text = resp.body?.string() ?: throw Exception("Empty refresh token response")
        if (!resp.isSuccessful) throw Exception("Refresh failed ${resp.code}: $text")

        val json = JSONObject(text)
        val newAccess = json.getString("access_token")
        val newRefresh = json.optString("refresh_token", refreshToken)
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

        Triple(newAccess, newRefresh, expiresAt)
    }
}
