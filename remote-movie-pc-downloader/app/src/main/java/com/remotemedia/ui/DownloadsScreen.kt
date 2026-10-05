package com.remotemedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.StorageManager
import com.remotemedia.download.DownloadItem
import com.remotemedia.download.TorrentEngine
import com.remotemedia.ui.theme.*
import java.io.File

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FolderOpen
import androidx.core.content.FileProvider

@Composable
fun DownloadsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloadItems by TorrentEngine.downloads.collectAsState()
    val torrentCount = downloadItems.count { it.source.contains("TORRENT", ignoreCase = true) }
    val telegramCount = downloadItems.count { it.source.contains("TELEGRAM", ignoreCase = true) }
    val userCount = downloadItems.count { it.source.contains("HTTP", ignoreCase = true) || it.source.contains("USER", ignoreCase = true) }
    val activeCount = downloadItems.count { it.progressPct < 100 }
    val completedCount = downloadItems.count { it.progressPct == 100 }
    val scrollState = rememberScrollState()

    val moviesDir = StorageManager.getMoviesDir()
    val isSandbox = StorageManager.isScopedSandbox(moviesDir)
    var showInAppBrowser by remember { mutableStateOf(false) }

    if (showInAppBrowser) {
        InAppMediaBrowserDialog(
            folder = moviesDir,
            onDismiss = { showInAppBrowser = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Summary Status Card ────────────────────────────────────────────────
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
                Column {
                    Text("// QUEUE_STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (downloadItems.isNotEmpty()) "$activeCount ACTIVE / $completedCount DONE" else "ALL TRANSFERS IDLE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (downloadItems.isNotEmpty()) StatusGreen else TacticalMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .background(if (activeCount > 0) StatusGreen.copy(alpha = 0.15f) else TacticalSurface, RoundedCornerShape(3.dp))
                        .border(1.dp, if (activeCount > 0) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${downloadItems.size} TOTAL ITEMS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (activeCount > 0) StatusGreen else TacticalText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown Pills
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadPill("TORRENT", torrentCount, TacticalOrange, modifier = Modifier.weight(1f))
                DownloadPill("TELEGRAM", telegramCount, TacticalYellow, modifier = Modifier.weight(1f))
                DownloadPill("WEB / HTTP", userCount, ElectricTeal, modifier = Modifier.weight(1f))
            }
        }

        // ── Storage Destination Target Card ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Folder", tint = TacticalOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DOWNLOAD STORAGE FOLDER", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
                Text(if (isSandbox) "APP SANDBOX" else "PUBLIC STORAGE", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (isSandbox) TacticalYellow else StatusGreen)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                moviesDir.absolutePath,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalOrange,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isSandbox) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalYellow.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = TacticalYellow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Android hides /Android/data/ from external file manager apps. Use 'BROWSE DOWNLOADS' below to view, play with VLC, or export files anytime!",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalYellow,
                        lineHeight = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: In-App Browser, External File Manager & Copy Path
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .background(TacticalOrange, RoundedCornerShape(4.dp))
                        .clickable { showInAppBrowser = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = TacticalBg, modifier = Modifier.size(14.dp))
                        Text("BROWSE DOWNLOADS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalBg)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TacticalPanel, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable { openInFileManager(context, moviesDir) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = TacticalText, modifier = Modifier.size(14.dp))
                        Text("FILES APP", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .background(TacticalPanel, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Download Location", moviesDir.absolutePath))
                            Toast.makeText(context, "Location copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = TacticalText, modifier = Modifier.size(14.dp))
                        Text("COPY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                }
            }
        }

        // ── Downloads Item List Header ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("// TRANSFER_QUEUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            if (completedCount > 0) {
                Box(
                    modifier = Modifier
                        .background(TacticalSurface, RoundedCornerShape(3.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable { TorrentEngine.clearCompleted() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("CLEAR FINISHED", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
            }
        }

        // ── Downloads Content ──────────────────────────────────────────────────
        if (downloadItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 42.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("DOWNLOAD QUEUE IS EMPTY", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Paste a magnet link or send a movie name to your Telegram bot.\nPocketNode downloads files directly into your server storage folder.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                downloadItems.forEach { item ->
                    FullDownloadItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun InAppMediaBrowserDialog(
    folder: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fileList by remember { mutableStateOf(StorageManager.listFiles(folder)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(TacticalPanel, RoundedCornerShape(8.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = TacticalOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DOWNLOADED FILES", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
                Box(
                    modifier = Modifier
                        .background(TacticalSurface, RoundedCornerShape(3.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(folder.absolutePath, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(12.dp))

            if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📂", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NO DOWNLOADED FILES YET", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Completed downloads will appear here automatically.", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileList) { file: File ->
                        val sizeStr = TorrentEngine.formatSize(file.length())
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                file.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Size: $sizeStr",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalOrange
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // PLAY Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                        .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                                        .clickable { openMediaFile(context, file) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(12.dp))
                                        Text("PLAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                                    }
                                }

                                // SHARE Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(TacticalPanel, RoundedCornerShape(3.dp))
                                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                        .clickable { shareMediaFile(context, file) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(12.dp))
                                        Text("SHARE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                                    }
                                }

                                // DELETE Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(TacticalPanel, RoundedCornerShape(3.dp))
                                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                        .clickable {
                                            try {
                                                file.delete()
                                                fileList = StorageManager.listFiles(folder)
                                                Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = TacticalRed, modifier = Modifier.size(12.dp))
                                        Text("DELETE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openMediaFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = StorageManager.getMimeFromExtension(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Play with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMediaFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = StorageManager.getMimeFromExtension(file.name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openInFileManager(context: Context, folder: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Folder Location", folder.absolutePath))
        Toast.makeText(context, "Location copied: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun DownloadPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, if (count > 0) color.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (count > 0) color else TacticalText)
        }
    }
}

@Composable
private fun FullDownloadItemCard(item: DownloadItem) {
    val sourceColor = when {
        item.source.contains("TORRENT", true) -> TacticalOrange
        item.source.contains("TELEGRAM", true) -> TacticalYellow
        else -> ElectricTeal
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                Box(
                    modifier = Modifier
                        .background(sourceColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                        .border(0.5.dp, sourceColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.source.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = sourceColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    item.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${item.progressPct}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (item.progressPct == 100) StatusGreen else TacticalYellow
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(TacticalSurface, RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((item.progressPct / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(if (item.progressPct == 100) StatusGreen else sourceColor, RoundedCornerShape(2.dp))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isTelegram = item.source.equals("TELEGRAM", ignoreCase = true)

        // ── Transfer Metrics Grid ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(4.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isTelegram) {
                // Telegram specific stream info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "✈ TELEGRAM CLOUD MEDIA",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2AABEE)
                    )
                    Text(
                        if (item.progressPct == 100) "SAVED IN MOVIES" else "RESUMABLE CHUNKS (4GB MODE)",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (item.progressPct == 100) StatusGreen else TacticalOrange
                    )
                }
            } else {
                // Row 1: Seeds & Peers & Share Ratio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "▲ Seeds: ${item.seeds}${if (item.totalSeeds > 0) " (${item.totalSeeds})" else ""}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = StatusGreen
                        )
                        Text(
                            "▼ Peers: ${item.peers}${if (item.totalPeers > 0) " (${item.totalPeers})" else ""}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalOrange
                        )
                    }
                    Text(
                        "Ratio: ${"%.2f".format(item.shareRatio)}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                }
            }

            // Row 2: DL Speed, UP Speed & ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "⬇ DL: ${item.downSpeed}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (item.downSpeed != "0 KB/s") StatusGreen else TacticalMuted
                    )
                    if (!isTelegram) {
                        Text(
                            "⬆ UP: ${item.upSpeed}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )
                    } else {
                        Text(
                            "CHUNKED HTTP",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )
                    }
                }
                Text(
                    "ETA: ${item.eta}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (item.eta == "Done") StatusGreen else TacticalYellow
                )
            }

            // Row 3: Hash & Destination info
            if (item.hash.isNotBlank() || item.savePath.isNotBlank() || isTelegram) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (item.hash.isNotBlank()) {
                        Text(
                            "HASH: ${item.hash.take(12)}...",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted.copy(alpha = 0.7f)
                        )
                    } else if (isTelegram) {
                        Text(
                            "SOURCE: TELEGRAM API",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted.copy(alpha = 0.7f)
                        )
                    }
                    val saveFolder = if (item.savePath.isNotBlank()) {
                        try { File(item.savePath).name.ifEmpty { "Movies" } } catch (_: Exception) { "Movies" }
                    } else "Movies"
                    Text(
                        "SAVE: .../$saveFolder",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val displayLabel = if (isTelegram && item.speedLabel.contains("SWARM", true)) {
                    if (item.progressPct == 100) "COMPLETED" else if (item.isPaused) "PAUSED" else "DOWNLOADING TELEGRAM"
                } else {
                    item.speedLabel.ifEmpty { if (item.progressPct == 100) "COMPLETED" else "DOWNLOADING" }
                }

                Text(
                    displayLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        item.isPaused -> TacticalYellow
                        item.progressPct == 100 -> StatusGreen
                        displayLabel.contains("DOWNLOADING", true) -> StatusGreen
                        displayLabel.contains("RESOLVING", true) || displayLabel.contains("CONNECTING", true) || displayLabel.contains("SWARM", true) || displayLabel.contains("RECONNECTING", true) -> TacticalOrange
                        else -> TacticalMuted
                    }
                )
                val downloadedStr = TorrentEngine.formatSize(item.downloadedBytes)
                val totalStr = if (item.totalBytes > 0) TorrentEngine.formatSize(item.totalBytes) else item.sizeLabel
                val remBytes = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0L)
                val leftStr = if (remBytes > 0 && item.totalBytes > 0 && item.progressPct < 100) " (${TorrentEngine.formatSize(remBytes)} left)" else ""
                val sizeDisplay = when {
                    item.progressPct == 100 -> "$totalStr • DOWNLOAD COMPLETE"
                    item.totalBytes > 0 || item.downloadedBytes > 0 -> "$downloadedStr / $totalStr$leftStr"
                    else -> "Target Size: $totalStr"
                }

                Text(
                    sizeDisplay,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.progressPct < 100 && item.seeds == 0 && item.peers == 0 && item.source.contains("TORRENT", true)) {
                    Box(
                        modifier = Modifier
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalOrange.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .clickable { TorrentEngine.resumeDownload(item.id) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = TacticalOrange,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                "RETRY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalOrange
                            )
                        }
                    }
                }

                if (item.progressPct < 100) {
                    Box(
                        modifier = Modifier
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, if (item.isPaused) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                            .clickable {
                                if (item.isPaused) TorrentEngine.resumeDownload(item.id)
                                else TorrentEngine.pauseDownload(item.id)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (item.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (item.isPaused) "Resume" else "Pause",
                                tint = if (item.isPaused) StatusGreen else TacticalYellow,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (item.isPaused) "RESUME" else "PAUSE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (item.isPaused) StatusGreen else TacticalYellow
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(TacticalSurface, RoundedCornerShape(3.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable { TorrentEngine.deleteDownload(item.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TacticalRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "DELETE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalRed
                        )
                    }
                }
            }
        }
    }
}
