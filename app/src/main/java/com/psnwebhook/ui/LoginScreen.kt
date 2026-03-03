package com.psnwebhook.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Games
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psnwebhook.BuildConfig

@Composable
fun LoginScreen(onLoginClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.GradTop, C.GradMid, C.GradBot)))) {
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .blur(80.dp)
                .background(C.Primary.copy(alpha = 0.15f * pulse), CircleShape)
        )

        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(100.dp)
                    .background(C.Primary.copy(0.2f), RoundedCornerShape(28.dp))
                    .border(2.dp, C.Accent.copy(0.4f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Games, null, tint = C.Cyan, modifier = Modifier.size(52.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text("PSN Webhook", fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = C.White)
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = C.Muted.copy(0.5f))
            Spacer(Modifier.height(6.dp))
            Text("Spoof your PlayStation presence", fontSize = 14.sp, color = C.Muted)

            Spacer(Modifier.height(36.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(C.Card, RoundedCornerShape(16.dp))
                    .border(1.dp, C.Border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureRow(Icons.Outlined.Games,       "Show any PSN game as your current activity")
                FeatureRow(Icons.Outlined.Info,        "View your profile, level and trophies")
                FeatureRow(Icons.Outlined.Security,    "Auto-refresh presence in background")
                FeatureRow(Icons.Outlined.VisibilityOff, "Data stays local — never shared")
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Login, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sign in with PlayStation", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Sign in with your Sony account to continue",
                fontSize = 11.sp, color = C.Muted, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = C.Accent, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = C.White.copy(0.85f))
    }
}
