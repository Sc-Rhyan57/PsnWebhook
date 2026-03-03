package com.psnwebhook.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PsnLoginActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context) = Intent(context, PsnLoginActivity::class.java)
        private const val PSN_LOGIN_URL = "https://my.playstation.com/"
        private const val NPSSO_CHECK_URL = "https://ca.account.sony.com/api/v1/ssocookie"
    }

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var alreadyProcessed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().removeAllCookies(null)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
            webViewClient = PsnWebViewClient()
        }
        val frame = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(frame)
        webView?.loadUrl(PSN_LOGIN_URL)
    }

    private fun showLoading(msg: String) {
        handler.post {
            val html = "<html><body style='background:#00041A;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0'><div style='text-align:center'><div style='font-size:36px;margin-bottom:16px'>🎮</div><div style='font-size:16px;color:#00439C'>$msg</div></div></body></html>"
            val encoded = Base64.encodeToString(html.toByteArray(), Base64.DEFAULT)
            webView?.loadData(encoded, "text/html; charset=UTF-8", "base64")
        }
    }

    inner class PsnWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            checkForNpsso(url)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            checkForNpsso(request.url.toString())
            return false
        }

        private fun checkForNpsso(url: String) {
            if (alreadyProcessed) return
            if (url.contains("my.playstation.com") || url.contains("playstation.com/en-") || url.contains("sonyentertainmentnetwork.com")) {
                scope.launch {
                    try {
                        val npsso = fetchNpsso()
                        if (npsso.isNotEmpty()) {
                            alreadyProcessed = true
                            showLoading("Autenticando...")
                            processNpsso(npsso)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun fetchNpsso(): String {
        return try {
            val client = okhttp3.OkHttpClient.Builder().followRedirects(false).build()
            val cookies = CookieManager.getInstance().getCookie("https://ca.account.sony.com") ?: ""
            val req = okhttp3.Request.Builder()
                .url(NPSSO_CHECK_URL)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return ""
            if (body.contains("npsso")) org.json.JSONObject(body).optString("npsso", "") else ""
        } catch (_: Exception) { "" }
    }

    private suspend fun processNpsso(npsso: String) {
        try {
            val (accessToken, refreshToken, expiresAt) = PsnAuthManager.exchangeNpssoForTokens(npsso)
            handler.post {
                val result = Intent().apply {
                    putExtra("npsso", npsso)
                    putExtra("accessToken", accessToken)
                    putExtra("refreshToken", refreshToken)
                    putExtra("expiresAt", expiresAt)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        } catch (e: Exception) {
            handler.post {
                val html = "<html><body style='background:#00041A;color:#EF4444;font-family:monospace;padding:20px'><h3>Erro</h3><pre>${e.message}</pre></body></html>"
                val encoded = Base64.encodeToString(html.toByteArray(), Base64.DEFAULT)
                webView?.loadData(encoded, "text/html; charset=UTF-8", "base64")
            }
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}
