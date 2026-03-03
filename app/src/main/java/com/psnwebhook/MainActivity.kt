package com.psnwebhook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psnwebhook.auth.PsnLoginActivity
import com.psnwebhook.model.AppScreen
import com.psnwebhook.ui.C
import com.psnwebhook.ui.HomeScreen
import com.psnwebhook.ui.LoginScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

private const val PREF_CRASH      = "psn_crash_prefs"
private const val KEY_CRASH_TRACE = "crash_trace"
private const val CURRENT_VERSION get() = BuildConfig.VERSION_NAME

private fun isNewerVersion(latest: String, current: String): Boolean {
    val l = latest.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
    val c = current.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(l.size, c.size)) {
        val lv = l.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (lv > cv) return true
        if (lv < cv) return false
    }
    return false
}

private suspend fun checkLatestVersion(): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
        val resp = client.newCall(
            Request.Builder()
                .url(BuildConfig.GITHUB_API_LATEST)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
        ).execute()
        if (!resp.isSuccessful) return@withContext null
        JSONObject(resp.body?.string() ?: return@withContext null)
            .optString("tag_name")
            .takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onLoginResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCrashHandler()

        val crashPrefs = getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
        val crashTrace = crashPrefs.getString(KEY_CRASH_TRACE, null)
        if (crashTrace != null) crashPrefs.edit().remove(KEY_CRASH_TRACE).apply()

        setContent {
            AppTheme {
                if (crashTrace != null) {
                    CrashScreen(trace = crashTrace)
                } else {
                    var updateTag by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        try {
                            val v = checkLatestVersion()
                            if (v != null && isNewerVersion(v, CURRENT_VERSION)) updateTag = v
                        } catch (_: Exception) {}
                    }

                    if (updateTag != null) {
                        UpdateDialog(
                            tag = updateTag!!,
                            current = CURRENT_VERSION,
                            onUpdate = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")))
                                updateTag = null
                            },
                            onDismiss = { updateTag = null }
                        )
                    }

                    PsnApp(
                        vm = vm,
                        onLaunchLogin = { loginLauncher.launch(PsnLoginActivity.intent(this)) }
                    )
                }
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val sw = StringWriter()
                exception.printStackTrace(PrintWriter(sw))
                val log = buildString {
                    appendLine("PSN Webhook — Crash Report")
                    appendLine("Version   : $CURRENT_VERSION")
                    appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread    : ${thread.name}")
                    appendLine("---")
                    append(sw.toString())
                }
                getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH_TRACE, log)
                    .commit()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(thread, exception)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(2)
        }
    }

    @Composable
    fun CrashScreen(trace: String) {
        val ctx = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BugReport, null, tint = C.Error, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("App Crashed", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = C.White)
                        Text("v$CURRENT_VERSION", fontSize = 10.sp, color = C.Muted, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                    Text("Close", color = C.Error, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "An unexpected error occurred. Copy the log below and report it on GitHub.",
                fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    (ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Crash Log", trace))
                },
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Crash Log", fontWeight = FontWeight.Bold)
            }

            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = C.Error.copy(0.08f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            trace,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = C.Error.copy(0.9f),
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/issues/new"))
                    )
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.CardAlt),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.BugReport, null, modifier = Modifier.size(16.dp), tint = C.Muted)
                Spacer(Modifier.width(8.dp))
                Text("Report on GitHub", color = C.Muted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun UpdateDialog(tag: String, current: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Update, null, tint = C.Accent, modifier = Modifier.size(32.dp)) },
        title = { Text("Update Available", color = C.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Version ${tag.trimStart('v')} is available.\nYou are currently on v$current.",
                color = C.Muted
            )
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Update", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later", color = C.Muted) }
        },
        containerColor = C.Surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary    = C.Primary,
            background = C.Bg,
            surface    = C.Surface,
        ),
        content = content
    )
}

@Composable
fun PsnApp(vm: MainViewModel, onLaunchLogin: () -> Unit) {
    val screen        by vm.screen.collectAsState()
    val account       by vm.account.collectAsState()
    val games         by vm.games.collectAsState()
    val presence      by vm.presence.collectAsState()
    val trophies      by vm.trophies.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()
    val customTitleId by vm.customTitleId.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        containerColor = C.Bg,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = C.Error.copy(0.9f), contentColor = Color.White)
            }
        },
        bottomBar = {
            Column {
                Footer()
                GitHubButton()
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.LOGIN -> LoginScreen(onLoginClick = onLaunchLogin)
                AppScreen.HOME  -> {
                    if (account != null) {
                        HomeScreen(
                            account           = account!!,
                            games             = games,
                            presence          = presence,
                            trophies          = trophies,
                            isLoading         = isLoading,
                            customTitleId     = customTitleId,
                            onCustomTitleChange = vm::setCustomTitleId,
                            onStartPresence   = vm::startPresence,
                            onStopPresence    = vm::stopPresence,
                            onRefresh         = vm::refreshData,
                            onLogout          = vm::logout,
                            currentVersion    = CURRENT_VERSION
                        )
                    }
                }
            }
            if (isLoading && screen == AppScreen.LOGIN) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.Accent)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    val t   = rememberInfiniteTransition(label = "rgb")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "h")
    val c   = Color.hsv(hue, 0.75f, 1f)
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("By Rhyan57", fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
        }
        Text("v$CURRENT_VERSION", fontSize = 9.sp, color = C.Muted.copy(0.5f), fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun GitHubButton() {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")))
        },
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = C.Muted),
        border   = BorderStroke(1.dp, C.Border),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp), tint = C.Muted)
        Spacer(Modifier.width(8.dp))
        Text(BuildConfig.GITHUB_REPO, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
