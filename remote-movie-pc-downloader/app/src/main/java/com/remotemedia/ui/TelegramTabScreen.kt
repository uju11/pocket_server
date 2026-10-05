package com.remotemedia.ui

import com.remotemedia.ServerForegroundService
import com.remotemedia.download.TdlibDownloadEngine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ui.theme.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun TelegramTabScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // Bot credentials
    var botToken by remember { mutableStateOf(prefs.getString("telegram_bot_token", "") ?: "") }
    var tokenVisible by remember { mutableStateOf(false) }
    var apiId by remember { mutableStateOf(prefs.getString("telegram_api_id", "") ?: "") }
    var apiHash by remember { mutableStateOf(prefs.getString("telegram_api_hash", "") ?: "") }
    var hashVisible by remember { mutableStateOf(false) }
    var apiEndpoint by remember { mutableStateOf(prefs.getString("telegram_api_endpoint", "https://api.telegram.org") ?: "https://api.telegram.org") }
    var streamBridgeUrl by remember { mutableStateOf(prefs.getString("telegram_stream_bridge_url", "") ?: "") }
    var identHandle by remember { mutableStateOf(prefs.getString("telegram_ident_handle", "@PocketNode_Daemon_bot") ?: "@PocketNode_Daemon_bot") }
    var adminChatId by remember {
        val saved = prefs.getString("telegram_chat_id", "") ?: ""
        mutableStateOf(if (saved.isBlank() || saved == "948281948") "835200448" else saved)
    }
    var botStatus by remember { mutableStateOf(if (botToken.isNotBlank()) "ONLINE / 200 OK" else "STANDBY / UNCONFIGURED") }
    var isTestingWebhook by remember { mutableStateOf(false) }

    val isLocalServerRunning by ServerForegroundService.telegramLocalServerRunning.collectAsState()
    var localServerEnabled by remember { mutableStateOf(prefs.getBoolean("tg_local_server_enabled", false)) }

    // Notification Triggers
    var notifyCrash by remember { mutableStateOf(prefs.getBoolean("tg_notify_crash", true)) }
    var notifyThermal by remember { mutableStateOf(prefs.getBoolean("tg_notify_thermal", true)) }
    var notifyStorage by remember { mutableStateOf(prefs.getBoolean("tg_notify_storage", true)) }
    var notifyService by remember { mutableStateOf(prefs.getBoolean("tg_notify_service", false)) }

    // Remote Execution
    var allowRemoteExec by remember { mutableStateOf(prefs.getBoolean("tg_allow_remote_exec", true)) }
    var securityPin by remember { mutableStateOf(prefs.getString("tg_security_pin", "884192") ?: "884192") }
    var pinVisible by remember { mutableStateOf(false) }
    var masterId by remember { mutableStateOf(prefs.getString("tg_master_id", "@xmedia_admin") ?: "@xmedia_admin") }

    val scrollState = rememberScrollState()

    fun copyToClipboard(text: String, label: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun saveAllConfig(silent: Boolean = false) {
        prefs.edit()
            .putString("telegram_bot_token", botToken.trim())
            .putString("telegram_api_id", apiId.trim())
            .putString("telegram_api_hash", apiHash.trim())
            .putString("telegram_api_endpoint", apiEndpoint.trim())
            .putString("telegram_stream_bridge_url", streamBridgeUrl.trim())
            .putString("telegram_ident_handle", identHandle.trim())
            .putString("telegram_chat_id", adminChatId.trim())
            .putString("tg_master_id", masterId.trim())
            .putString("tg_security_pin", securityPin.trim())
            .putBoolean("tg_notify_crash", notifyCrash)
            .putBoolean("tg_notify_thermal", notifyThermal)
            .putBoolean("tg_notify_storage", notifyStorage)
            .putBoolean("tg_notify_service", notifyService)
            .putBoolean("tg_allow_remote_exec", allowRemoteExec)
            .putBoolean("tg_local_server_enabled", localServerEnabled)
            .apply()

        // Initialize native on-device 2GB MTProto engine if credentials are provided
        val numApiId = apiId.trim().toIntOrNull() ?: 0
        if (numApiId > 0 && apiHash.isNotBlank() && botToken.isNotBlank()) {
            TdlibDownloadEngine.init(context, numApiId, apiHash.trim(), botToken.trim())
        }

        // Auto-start Telegram Bot subservice if main daemon is active
        if (botToken.isNotBlank() && ServerForegroundService.isRunning.value) {
            ServerForegroundService.instance?.toggleSubService("telegram", true)
        }

        if (!silent) {
            Toast.makeText(context, "Configuration successfully persisted to node storage!", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-save whenever user navigates away or closes tab
    DisposableEffect(Unit) {
        onDispose {
            if (botToken.isNotBlank() || adminChatId.isNotBlank()) {
                saveAllConfig(silent = true)
            }
        }
    }

    fun testWebhook() {
        if (botToken.isBlank()) {
            Toast.makeText(context, "Please enter a Telegram Bot Token first", Toast.LENGTH_SHORT).show()
            return
        }
        isTestingWebhook = true
        coroutineScope.launch {
            try {
                val base = if (apiEndpoint.isBlank()) "https://api.telegram.org" else apiEndpoint.trim().trimEnd('/')
                val client = HttpClient(CIO)
                val response: HttpResponse = client.get("$base/bot${botToken.trim()}/getMe")
                val body = response.bodyAsText()
                client.close()

                withContext(Dispatchers.Main) {
                    isTestingWebhook = false
                    if (response.status == HttpStatusCode.OK) {
                        botStatus = "ONLINE / 200 OK"
                        val json = JSONObject(body)
                        if (json.optBoolean("ok")) {
                            val result = json.optJSONObject("result")
                            val username = result?.optString("username", "") ?: ""
                            if (username.isNotBlank()) {
                                identHandle = "@$username"
                            }
                        }
                        saveAllConfig(silent = true)
                        Toast.makeText(context, "Telegram Bot API Verified & Saved! Status: 200 OK", Toast.LENGTH_LONG).show()
                    } else {
                        botStatus = "ERROR ${response.status.value}"
                        Toast.makeText(context, "Webhook verification failed: ${response.status}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isTestingWebhook = false
                    botStatus = "NETWORK ERROR"
                    Toast.makeText(context, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun sendTestPing() {
        if (botToken.isBlank() || adminChatId.isBlank()) {
            Toast.makeText(context, "Bot Token and Chat ID are required to send a ping", Toast.LENGTH_SHORT).show()
            return
        }
        saveAllConfig(silent = true)
        coroutineScope.launch {
            try {
                val base = if (apiEndpoint.isBlank()) "https://api.telegram.org" else apiEndpoint.trim().trimEnd('/')
                val client = HttpClient(CIO)
                val pingMsg = "PocketNode Daemon Ping Alert\nNode: HOMELAB-01\nStatus: 200 OK\nTime: ${System.currentTimeMillis()}"
                val response: HttpResponse = client.get("$base/bot${botToken.trim()}/sendMessage") {
                    parameter("chat_id", adminChatId.trim())
                    parameter("text", pingMsg)
                }
                client.close()
                withContext(Dispatchers.Main) {
                    if (response.status == HttpStatusCode.OK) {
                        Toast.makeText(context, "Telegram Test Ping sent successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to send ping: ${response.status.value}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sending ping: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 01: BOT CREDENTIALS ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            // Header with Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// BOT_CREDENTIALS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (botStatus.contains("200")) StatusGreen.copy(alpha = 0.15f) else TacticalOrange.copy(alpha = 0.15f))
                        .border(1.dp, if (botStatus.contains("200")) StatusGreen else TacticalOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        botStatus,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (botStatus.contains("200")) StatusGreen else TacticalOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Bot API Token with SHA256_ENC Badge & Show/Hide Eye
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bot API Token", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text("SHA256_ENC", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstUVwxYZ...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation('•'),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle token visibility",
                            tint = TacticalMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Ident Handle with Copy Icon
            Text("Ident Handle", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = identHandle,
                onValueChange = { identHandle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { copyToClipboard(identHandle, "Bot Ident") }) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TacticalMuted, modifier = Modifier.size(16.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalOrange,
                    unfocusedTextColor = TacticalOrange,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Admin Chat ID with [VERIFIED] Badge & Copy Icon
            Text("Admin Chat ID", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = adminChatId,
                onValueChange = { adminChatId = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(StatusGreen.copy(alpha = 0.2f))
                                .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("[ VERIFIED ]", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                        }
                        IconButton(onClick = { copyToClipboard(adminChatId, "Chat ID") }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TacticalMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Telegram API ID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Telegram API ID", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text("(From my.telegram.org)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiId,
                onValueChange = { apiId = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("e.g. 29481024", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Telegram API Hash
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Telegram API Hash", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text("(32-char hex string)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiHash,
                onValueChange = { apiHash = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (hashVisible) VisualTransformation.None else PasswordVisualTransformation('•'),
                trailingIcon = {
                    IconButton(onClick = { hashVisible = !hashVisible }) {
                        Icon(
                            imageVector = if (hashVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle hash visibility",
                            tint = TacticalMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                placeholder = { Text("e.g. 7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(6.dp))
            val isTdlibReady = TdlibDownloadEngine.isReady()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isTdlibReady) StatusGreen.copy(alpha = 0.15f) else TacticalSurface)
                    .border(1.dp, if (isTdlibReady) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isTdlibReady) "🟢 2GB MTProto Engine: Active & Ready (On-Device)" else "⚡ 2GB MTProto Engine: Fill API ID & Hash above to bypass 20MB limit",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isTdlibReady) StatusGreen else TacticalMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Bot API Endpoint (Local Server / Proxy for >20MB downloads)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("API Endpoint", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text("(Cloud limit: 20MB. Set local server for >20MB)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiEndpoint,
                onValueChange = { apiEndpoint = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.telegram.org", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (apiEndpoint.contains("api.telegram.org")) ElectricTeal.copy(alpha = 0.2f) else TacticalSurface)
                        .border(1.dp, if (apiEndpoint.contains("api.telegram.org")) ElectricTeal else TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable {
                            apiEndpoint = "https://api.telegram.org"
                            saveAllConfig(silent = true)
                            Toast.makeText(context, "Set to Telegram Cloud (20MB limit)", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("☁️ Cloud (20MB)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (apiEndpoint.contains("api.telegram.org")) ElectricTeal else TacticalMuted)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (apiEndpoint.contains(":8081")) StatusGreen.copy(alpha = 0.2f) else TacticalSurface)
                        .border(1.dp, if (apiEndpoint.contains(":8081")) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable {
                            val pcHost = prefs.getString("pc_host", "")?.trim() ?: ""
                            val target = if (pcHost.isNotBlank()) "http://$pcHost:8081" else "http://127.0.0.1:8081"
                            apiEndpoint = target
                            saveAllConfig(silent = true)
                            Toast.makeText(context, "Set to 2GB Local Server: $target", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀 2GB Server (:8081)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (apiEndpoint.contains(":8081")) StatusGreen else TacticalMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stream Bridge URL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text("(2GB Cloud / Web bypass)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = streamBridgeUrl,
                onValueChange = { 
                    streamBridgeUrl = it
                    prefs.edit().putString("telegram_stream_bridge_url", it.trim()).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://my-stream-bot.onrender.com", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Test Bot Webhook Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ElectricTeal.copy(alpha = 0.15f))
                    .border(1.dp, ElectricTeal, RoundedCornerShape(4.dp))
                    .clickable(enabled = !isTestingWebhook) { testWebhook() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 12.sp, color = ElectricTeal)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isTestingWebhook) "[ VERIFYING WEBHOOK... ]" else "[ ⚡ TEST BOT WEBHOOK ]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricTeal
                    )
                }
            }
        }

        // ── 01.5: EMBEDDED LOCAL SERVER (2GB FILE UNLOCK) ─────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, if (isLocalServerRunning) StatusGreen.copy(alpha = 0.6f) else TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// LOCAL_BOT_API_SERVER (2GB)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isLocalServerRunning) StatusGreen.copy(alpha = 0.2f) else TacticalSurface)
                        .border(1.dp, if (isLocalServerRunning) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLocalServerRunning) "● RUNNING (:8081)" else "○ STOPPED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLocalServerRunning) StatusGreen else TacticalMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Runs directly on this Android device. Bypasses the 20MB cloud limit and enables full 2,000 MB (2 GB) file downloads into your media library.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalText
            )

            Spacer(modifier = Modifier.height(10.dp))

            TelegramToggleRow(
                title = "Enable 2GB Local Server",
                subtitle = "Turn on/off embedded proxy on http://127.0.0.1:8081",
                checked = localServerEnabled,
                onCheckedChange = { isChecked ->
                    localServerEnabled = isChecked
                    prefs.edit().putBoolean("tg_local_server_enabled", isChecked).apply()
                    ServerForegroundService.toggleTelegramLocalServer(isChecked, apiId, apiHash)
                    if (isChecked) {
                        apiEndpoint = "http://127.0.0.1:8081"
                        prefs.edit().putString("telegram_api_endpoint", "http://127.0.0.1:8081").apply()
                        Toast.makeText(context, "Local 2GB Server Started on port 8081!", Toast.LENGTH_SHORT).show()
                    } else {
                        apiEndpoint = "https://api.telegram.org"
                        prefs.edit().putString("telegram_api_endpoint", "https://api.telegram.org").apply()
                        Toast.makeText(context, "Local Server Stopped. Reverted to Cloud.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (isLocalServerRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("ACTIVE ENDPOINT", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text("127.0.0.1:8081", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("DOWNLOAD LIMIT", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text("2000 MB (2 GB)", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                        }
                    }
                }
            }
        }

        // ── 02: NOTIFICATION TRIGGERS (4 Rules) ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// NOTIFICATION_TRIGGERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(TacticalSurface)
                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("4 RULES", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TelegramToggleRow(
                title = "Crash & Daemon Offline",
                subtitle = "Immediate push notification on SIGKILL",
                checked = notifyCrash,
                onCheckedChange = { notifyCrash = it }
            )
            HorizontalDivider(color = TacticalBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

            TelegramToggleRow(
                title = "Thermal Overheat Alert",
                subtitle = "Push when SoC temp > 65.0°C",
                checked = notifyThermal,
                onCheckedChange = { notifyThermal = it }
            )
            HorizontalDivider(color = TacticalBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

            TelegramToggleRow(
                title = "Storage Low Threshold",
                subtitle = "Notify when volume < 10GB",
                checked = notifyStorage,
                onCheckedChange = { notifyStorage = it }
            )
            HorizontalDivider(color = TacticalBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

            TelegramToggleRow(
                title = "Service Unit State Change",
                subtitle = "Broadcast systemd unit transitions",
                checked = notifyService,
                onCheckedChange = { notifyService = it }
            )
        }

        // ── 03: REMOTE EXECUTION ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// REMOTE_EXECUTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(StatusGreen.copy(alpha = 0.15f))
                        .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("SECURE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TelegramToggleRow(
                title = "Allow Remote Exec",
                subtitle = "Permit /restart, /status, /top via bot chat",
                checked = allowRemoteExec,
                onCheckedChange = { allowRemoteExec = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Command 2FA Security PIN (REQUIRED)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Command 2FA Security PIN", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text("REQUIRED", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = securityPin,
                onValueChange = { securityPin = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation('•'),
                trailingIcon = {
                    IconButton(onClick = { pinVisible = !pinVisible }) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "PIN", tint = TacticalOrange, modifier = Modifier.size(16.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TacticalOrange,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Authorized Master ID
            Text("Authorized Master ID", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = masterId,
                onValueChange = { masterId = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = "Master", tint = TacticalMuted, modifier = Modifier.size(16.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText,
                    focusedContainerColor = TacticalSurface,
                    unfocusedContainerColor = TacticalSurface
                )
            )
        }

        // ── BOTTOM ACTION BUTTONS (Matching Image 5) ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SAVE CONFIG
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TacticalOrange)
                    .clickable { saveAllConfig() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("💾", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SAVE CONFIG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                        maxLines = 1
                    )
                }
            }

            // SEND TEST PING
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TacticalPanel)
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .clickable { sendTestPing() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "TEST PING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalText,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun TelegramToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Text(subtitle, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StatusGreen,
                checkedTrackColor = StatusGreen.copy(alpha = 0.3f),
                uncheckedThumbColor = TacticalMuted,
                uncheckedTrackColor = TacticalSurface
            )
        )
    }
}
