package com.psnwebhook.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PsnAuthManager {

    private const val CLIENT_ID   = "09515159-7237-4370-9b40-3806e67c0891"
    private const val SCOPE       = "psn:mobile.v2.core psn:clientapp"
    private const val REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect"
    private const val OAUTH_CODE_URL  = "https://ca.account.sony.com/api/authz/v3/oauth/authorize"
    private const val OAUTH_TOKEN_URL = "https://ca.account.sony.com/api/authz/v3/oauth/token"

    private const val BASIC_AUTH =
        "MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private fun buildHeaders(npsso: String? = null): Map<String, String> {
        val ua = "PlayStation Android(Prod)/v20.7.0-374709 (google; sdk_gphone64_arm64; Android OS 14/34)"
        val base = mutableMapOf(
            "User-Agent"     to ua,
            "Accept"         to "application/json",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br",
            "X-App-Ver"      to "20.7.0.374709",
            "X-Req-Id"       to java.util.UUID.randomUUID().toString(),
            "Content-Type"   to "application/x-www-form-urlencoded",
        )
        if (npsso != null) base["Cookie"] = "npsso=$npsso"
        return base
    }

    suspend fun exchangeNpssoForTokens(npsso: String): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val codeUrl = buildString {
            append(OAUTH_CODE_URL)
            append("?access_type=offline")
            append("&client_id=${java.net.URLEncoder.encode(CLIENT_ID, "UTF-8")}")
            append("&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
            append("&response_type=code")
            append("&scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}")
        }

        val codeReqBuilder = Request.Builder().url(codeUrl).get()
        buildHeaders(npsso).forEach { (k, v) -> codeReqBuilder.header(k, v) }
        val codeResp = http.newCall(codeReqBuilder.build()).execute()

        val location = codeResp.header("Location")
            ?: throw Exception("No redirect — check NPSSO validity. HTTP ${codeResp.code}")

        val code = if (location.contains("code=")) {
            location.substringAfter("code=").substringBefore("&").substringBefore("#")
        } else {
            throw Exception("No auth code in redirect: $location")
        }

        val tokenBody = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .add("token_format", "jwt")
            .build()

        val tokenReqBuilder = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .header("Authorization", "Basic $BASIC_AUTH")
            .post(tokenBody)
        buildHeaders(npsso).forEach { (k, v) -> tokenReqBuilder.header(k, v) }

        val tokenResp = http.newCall(tokenReqBuilder.build()).execute()
        val tokenText = tokenResp.body?.string() ?: throw Exception("Empty token response")
        if (!tokenResp.isSuccessful) throw Exception("Token exchange failed ${tokenResp.code}: $tokenText")

        val j = JSONObject(tokenText)
        val accessToken  = j.getString("access_token")
        val refreshToken = j.optString("refresh_token", "")
        val expiresIn    = j.optLong("expires_in", 3600L)
        Triple(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000L)
    }

    suspend fun refreshAccessToken(refreshToken: String): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_uri", REDIRECT_URI)
            .add("token_format", "jwt")
            .build()

        val reqBuilder = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .header("Authorization", "Basic $BASIC_AUTH")
            .post(body)
        buildHeaders().forEach { (k, v) -> reqBuilder.header(k, v) }

        val resp = http.newCall(reqBuilder.build()).execute()
        val text = resp.body?.string() ?: throw Exception("Empty refresh response")
        if (!resp.isSuccessful) throw Exception("Refresh failed ${resp.code}: $text")

        val j = JSONObject(text)
        val newAccess  = j.getString("access_token")
        val newRefresh = j.optString("refresh_token", refreshToken)
        val expiresIn  = j.optLong("expires_in", 3600L)
        Triple(newAccess, newRefresh, System.currentTimeMillis() + expiresIn * 1000L)
    }
}
