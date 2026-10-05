package com.remotemedia.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.StorageManager
import com.remotemedia.ui.theme.AccentGreen
import com.remotemedia.ui.theme.BorderGrey
import com.remotemedia.ui.theme.LightBackground
import com.remotemedia.ui.theme.PrimaryBlue
import com.remotemedia.ui.theme.SurfaceWhite
import com.remotemedia.ui.theme.TextDark
import com.remotemedia.ui.theme.TextMuted

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenJellyfinManager: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }

    var botToken by remember { mutableStateOf(prefs.getString("telegram_bot_token", "") ?: "") }
    var jellyfinPort by remember { mutableStateOf((if (prefs.getInt("jellyfin_port", 8096) == 8097) 8096 else prefs.getInt("jellyfin_port", 8096)).toString()) }
    var webPort by remember { mutableStateOf(prefs.getInt("web_port", 8080).toString()) }
    var customFolder by remember { mutableStateOf(StorageManager.getBaseDir().absolutePath) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
            }
            Text(
                text = "⚙ Server Configuration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Jellyfin Management Shortcut Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onOpenJellyfinManager,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🍿 Manage Jellyfin Libraries & Users", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Server Port Allocation",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = "Standard Jellyfin port is 8096 (compatible with Smart TVs & mobile apps).",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = jellyfinPort,
                        onValueChange = { jellyfinPort = it },
                        label = { Text("Jellyfin Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = TextMuted
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    OutlinedTextField(
                        value = webPort,
                        onValueChange = { webPort = it },
                        label = { Text("Web Dashboard Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = TextMuted
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Telegram Bot Integration",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = "Get token from Telegram @BotFather",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Telegram Bot Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextMuted
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                val isSandboxFolder = customFolder.contains("Android/data", ignoreCase = true)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 Storage Directory",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Text(
                        text = if (isSandboxFolder) "APP SANDBOX" else "PUBLIC STORAGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSandboxFolder) androidx.compose.ui.graphics.Color(0xFFE65100) else AccentGreen
                    )
                }

                if (isSandboxFolder) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ Notice: Android hides '/Android/data/' from user file managers. If you cannot find this folder on your phone, tap 'Switch to Public Storage' below so downloads appear directly in your Movies or Downloads folder.",
                        fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFE65100),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✅ Visible in all file managers and PC connections.",
                        fontSize = 11.sp,
                        color = AccentGreen,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = customFolder,
                    onValueChange = { customFolder = it },
                    label = { Text("Storage Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextMuted
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val success = StorageManager.switchToPublicStorage(context)
                            if (success) {
                                customFolder = StorageManager.getBaseDir().absolutePath
                                Toast.makeText(context, "Switched to Public Storage!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Grant 'All Files Access' first to use public storage.", Toast.LENGTH_LONG).show()
                                StorageManager.openStorageAccessSettings(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📁 Switch to Public Movies", fontSize = 11.sp, color = PrimaryBlue)
                    }

                    if (!StorageManager.isAllFilesAccessGranted()) {
                        OutlinedButton(
                            onClick = { StorageManager.openStorageAccessSettings(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔑 Grant Storage Permission", fontSize = 11.sp, color = PrimaryBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val parsedJellyfinPort = jellyfinPort.toIntOrNull() ?: 8096
                        val parsedWebPort = webPort.toIntOrNull() ?: 8080
                        val pathValid = StorageManager.setCustomStorageDir(context, customFolder.trim())

                        prefs.edit()
                            .putInt("jellyfin_port", parsedJellyfinPort)
                            .putInt("web_port", parsedWebPort)
                            .putString("telegram_bot_token", botToken.trim())
                            .apply()

                        Toast.makeText(context, "Settings saved! Jellyfin Port: $parsedJellyfinPort", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Configuration", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        prefs.edit().putBoolean("is_first_launch_completed", false).apply()
                        Toast.makeText(context, "Restart app to re-run setup wizard.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Re-Run Presentation Setup Wizard", color = PrimaryBlue)
                }
            }
        }
    }
}
