package com.psnwebhook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Games
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psnwebhook.model.PresenceConfig
import com.psnwebhook.model.PsnAccount
import com.psnwebhook.model.PsnGame

@Composable
fun HomeScreen(
    account: PsnAccount,
    games: List<PsnGame>,
    presence: PresenceConfig,
    trophies: Triple<Int, Int, Int>,
    isLoading: Boolean,
    customTitleId: String,
    onCustomTitleChange: (String) -> Unit,
    onStartPresence: (titleId: String, titleName: String) -> Unit,
    onStopPresence: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val t = rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "g"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.GradTop, C.GradMid, C.GradBot))),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PSN Webhook", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = C.White)
                    Text("Presença personalizada", fontSize = 11.sp, color = C.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, null, tint = if (isLoading) C.Accent else C.Muted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Outlined.Logout, null, tint = C.Error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item { ProfileCard(account, trophies) }

        item {
            if (presence.isActive) {
                ActivePresenceCard(presence, glow, onStopPresence)
            } else {
                PresenceSetupCard(
                    games = games,
                    customTitleId = customTitleId,
                    onCustomTitleChange = onCustomTitleChange,
                    onStart = onStartPresence
                )
            }
        }

        if (!presence.isActive && games.isNotEmpty()) {
            item {
                Text("Jogos Recentes", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.White)
            }
            items(games.take(10), key = { it.titleId }) { game ->
                GameRow(game = game, onSelect = { onStartPresence(game.titleId, game.name) })
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProfileCard(account: PsnAccount, trophies: Triple<Int, Int, Int>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, C.Border, RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(listOf(C.Primary.copy(0.6f), C.Blue2.copy(0.2f))),
                            CircleShape
                        )
                        .border(2.dp, C.Accent.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (account.onlineId.isNotEmpty()) {
                        Text(
                            account.onlineId.firstOrNull()?.uppercase() ?: "P",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = C.White
                        )
                    } else {
                        Icon(Icons.Outlined.AccountCircle, null, tint = C.Muted, modifier = Modifier.size(36.dp))
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        account.onlineId.ifEmpty { "Carregando..." },
                        color = C.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    when (account.presenceState.lowercase()) {
                                        "online" -> C.Success
                                        "away" -> C.Warning
                                        else -> C.Muted
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            account.presenceState.replaceFirstChar { it.uppercase() },
                            color = C.Muted,
                            fontSize = 12.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Nível", fontSize = 10.sp, color = C.Muted)
                    Text("${account.level}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = C.Cyan)
                }
            }

            HorizontalDivider(color = C.Border)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TrophyBadge("🥇", "${trophies.first}", "Ouro", C.Gold)
                TrophyBadge("🥈", "${trophies.second}", "Prata", C.Silver)
                TrophyBadge("🥉", "${trophies.third}", "Bronze", C.Bronze)
            }

            if (account.accountId.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.EmojiEvents, null, tint = C.Muted, modifier = Modifier.size(13.dp))
                    Text(
                        "ID: ${account.accountId.take(16)}…",
                        fontSize = 10.sp,
                        color = C.Muted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun TrophyBadge(emoji: String, count: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(emoji, fontSize = 20.sp)
        Text(count, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 9.sp, color = C.Muted)
    }
}

@Composable
private fun ActivePresenceCard(presence: PresenceConfig, glow: Float, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = C.Success.copy(0.08f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, C.Success.copy(0.4f * glow), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(C.Success.copy(glow), CircleShape)
                    )
                    Text("PRESENÇA ATIVA", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Success)
                }
                Text("AO VIVO", fontSize = 10.sp, color = C.Success, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = C.Success.copy(0.2f))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(C.Primary.copy(0.3f), RoundedCornerShape(10.dp))
                        .border(1.dp, C.Accent.copy(0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Games, null, tint = C.Accent, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(presence.titleName.ifEmpty { presence.titleId }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.White)
                    Text(presence.titleId, fontSize = 10.sp, color = C.Muted, fontFamily = FontFamily.Monospace)
                }
            }

            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Parar Presença", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PresenceSetupCard(
    games: List<PsnGame>,
    customTitleId: String,
    onCustomTitleChange: (String) -> Unit,
    onStart: (titleId: String, titleName: String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, C.Border, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(32.dp).background(C.Primary.copy(0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Games, null, tint = C.Accent, modifier = Modifier.size(16.dp))
                }
                Text("Configurar Presença", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.White)
            }

            HorizontalDivider(color = C.Border)

            Text("ID do Título PSN", fontSize = 11.sp, color = C.Muted, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = customTitleId,
                onValueChange = onCustomTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: PPSA01547_00", fontSize = 13.sp, color = C.Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Accent,
                    unfocusedBorderColor = C.Border,
                    focusedTextColor = C.White,
                    unfocusedTextColor = C.White,
                    cursorColor = C.Accent,
                    focusedContainerColor = C.CardAlt,
                    unfocusedContainerColor = C.CardAlt,
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Text(
                "Exemplos: PPSA01547_00 (GTA V), PPSA29660_00 (GTA VI), CUSA34085_00 (Elden Ring)",
                fontSize = 10.sp,
                color = C.Muted
            )

            Button(
                onClick = {
                    if (customTitleId.isNotBlank()) {
                        onStart(customTitleId.trim(), "")
                    }
                },
                enabled = customTitleId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ativar Presença", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameRow(game: PsnGame, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Card, RoundedCornerShape(12.dp))
            .border(1.dp, C.Border, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(C.Primary.copy(0.25f), RoundedCornerShape(10.dp))
                .border(1.dp, C.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                game.name.firstOrNull()?.uppercase() ?: "G",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = C.Accent
            )
        }
        Column(Modifier.weight(1f)) {
            Text(game.name, color = C.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(game.platform)
                Text(game.titleId, color = C.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Box(
            Modifier
                .background(C.Primary.copy(0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, C.Primary.copy(0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Usar", fontSize = 10.sp, color = C.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlatformBadge(platform: String) {
    val (bg, tx) = when (platform.uppercase()) {
        "PS5" -> C.Cyan.copy(0.12f) to C.Cyan
        "PS4" -> C.Accent.copy(0.12f) to C.Accent
        else -> C.Muted.copy(0.12f) to C.Muted
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(platform, fontSize = 8.sp, color = tx, fontWeight = FontWeight.Bold)
    }
}
