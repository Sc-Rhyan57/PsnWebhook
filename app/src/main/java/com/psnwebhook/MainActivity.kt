package com.psnwebhook

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onLoginResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                PsnApp(
                    vm = vm,
                    onLaunchLogin = { loginLauncher.launch(PsnLoginActivity.intent(this)) }
                )
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Primary,
            background = C.Bg,
            surface = C.Surface,
        ),
        content = content
    )
}

@Composable
fun PsnApp(vm: MainViewModel, onLaunchLogin: () -> Unit) {
    val screen by vm.screen.collectAsState()
    val account by vm.account.collectAsState()
    val games by vm.games.collectAsState()
    val presence by vm.presence.collectAsState()
    val trophies by vm.trophies.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
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
                Snackbar(
                    snackbarData = data,
                    containerColor = C.Error.copy(0.9f),
                    contentColor = Color.White
                )
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
                AppScreen.HOME -> {
                    if (account != null) {
                        HomeScreen(
                            account = account!!,
                            games = games,
                            presence = presence,
                            trophies = trophies,
                            isLoading = isLoading,
                            customTitleId = customTitleId,
                            onCustomTitleChange = vm::setCustomTitleId,
                            onStartPresence = { titleId, titleName -> vm.startPresence(titleId, titleName) },
                            onStopPresence = vm::stopPresence,
                            onRefresh = vm::refreshData,
                            onLogout = vm::logout
                        )
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.Accent)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    val t = rememberInfiniteTransition(label = "rgb")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "h")
    val c = Color.hsv(hue, 0.75f, 1f)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("By Rhyan57", fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
fun GitHubButton() {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sc-Rhyan57/PsnWebhook"))) },
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Muted),
        border = BorderStroke(1.dp, C.Border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp), tint = C.Muted)
        Spacer(Modifier.width(8.dp))
        Text("Sc-Rhyan57/PsnWebhook", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
