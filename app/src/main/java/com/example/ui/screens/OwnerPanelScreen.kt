package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BuiltInHtml5Games
import com.example.data.GameZipExtractor
import com.example.data.model.Game
import com.example.ui.components.GameThumbnailBanner
import com.example.ui.components.formatCountCompact
import com.example.ui.theme.BloxCardBorder
import com.example.ui.theme.BloxSurface
import com.example.ui.theme.BloxSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextSecondary

/**
 * Private & Hidden Owner Panel.
 * Accessible ONLY after verifying the Owner password ("games").
 * Normal users never see this screen or any of its upload/edit/delete buttons.
 */
@Composable
fun OwnerPanelScreen(
    allGames: List<Game>,
    onUploadNewGame: (
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String,
        featured: Boolean,
        gameType: String,
        apkPackageName: String,
        apkVersion: String,
        apkFileSize: Long
    ) -> Unit,
    onUpdateGame: (
        gameId: String,
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String,
        featured: Boolean,
        gameType: String?,
        apkPackageName: String?,
        apkVersion: String?,
        apkFileSize: Long?
    ) -> Unit,
    onMoveGamePosition: (Game, Boolean) -> Unit,
    onToggleGameStatus: (Game) -> Unit,
    onDeleteGame: (Game) -> Unit,
    onRefreshCatalog: () -> Unit,
    onPlayGame: (Game) -> Unit,
    onLockOwnerPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onLockOwnerPanel()
    }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingGame by remember { mutableStateOf<Game?>(null) }
    var gamePendingDelete by remember { mutableStateOf<Game?>(null) }

    val sortedGames = remember(allGames) {
        allGames.sortedWith(
            compareBy<Game> { if (it.sortOrder > 0L) it.sortOrder else Long.MAX_VALUE }
                .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )
    }

    val totalPlays = sortedGames.sumOf { it.plays }
    val totalLikes = sortedGames.sumOf { it.likes }
    val activeCount = sortedGames.count { it.isActive }
    val disabledCount = sortedGames.size - activeCount
    val estimatedPlayers = if (sortedGames.isEmpty()) 0L else (sortedGames.sumOf { it.ratingCount } + (totalPlays / 4L)).coerceAtLeast(1L)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Owner Dashboard Header
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BloxSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(NeonPink, NeonPurple, NeonCyan)),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(NeonPink.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Owner Vault",
                                    tint = NeonPink
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "OWNER CONTROL PANEL",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Private Admin Vault • Live Multi-Device Cloud Sync",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onLockOwnerPanel,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("lock_owner_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Owner Panel",
                                tint = NeonPink,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exit", color = NeonPink, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Telemetry Grid (Total Players, Total Game Plays, Total Likes, Active/Disabled)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BloxSurfaceVariant)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OwnerMetricItem("TOTAL PLAYERS", formatCountCompact(estimatedPlayers), NeonCyan)
                        OwnerMetricItem("TOTAL PLAYS", formatCountCompact(totalPlays), NeonGreen)
                        OwnerMetricItem("TOTAL LIKES", formatCountCompact(totalLikes), NeonPink)
                        OwnerMetricItem("ACTIVE / OFF", "$activeCount / $disabledCount", NeonGold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingGame = null
                                showEditorDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("owner_upload_game_button")
                        ) {
                            Icon(imageVector = Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload New Game", fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedButton(
                            onClick = onRefreshCatalog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("owner_refresh_cloud_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Cloud",
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Game Management List Header
        item {
            Text(
                text = "Your Uploaded Games (${sortedGames.size})",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        if (sortedGames.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BloxCardBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No games in your catalog yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Upload New Game' above to upload an HTML5 .ZIP package, .HTML/.JS file, or Game URL. Every game you upload stays permanently in its exact position across all devices until you delete it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            itemsIndexed(sortedGames, key = { _, g -> "owner_${g.id}" }) { index, game ->
                val positionNumber = index + 1
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (game.isActive) BloxCardBorder else NeonRed.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Fixed Position Badge (#1, #2, ...)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NeonPurple.copy(alpha = 0.22f))
                                        .border(1.dp, NeonPurple, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#$positionNumber",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = game.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (game.isActive) NeonGreen.copy(alpha = 0.2f)
                                                    else NeonRed.copy(alpha = 0.2f)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (game.isActive) "ACTIVE" else "DISABLED",
                                                color = if (game.isActive) NeonGreen else NeonRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        if (game.isApkGame) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(NeonCyan.copy(alpha = 0.2f))
                                                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "📦 APK",
                                                    color = NeonCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val typePrefix = if (game.isApkGame) "APK (${game.apkVersion.ifBlank { "v1.0" }})" else "HTML5 Web"
                                    Text(
                                        text = "$typePrefix • ${game.category} • ${formatCountCompact(game.plays)} plays • ${formatCountCompact(game.likes)} likes",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Fixed Position Reorder Buttons (Up / Down)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { onMoveGamePosition(game, true) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        tint = if (index > 0) NeonCyan else TextSecondary.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveGamePosition(game, false) },
                                    enabled = index < sortedGames.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        tint = if (index < sortedGames.lastIndex) NeonCyan else TextSecondary.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Owner Actions: Test Play, Edit/Replace Files, Enable/Disable, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPlayGame(game) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Test Play",
                                    tint = NeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    editingGame = game
                                    showEditorDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("owner_edit_${game.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Game",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onToggleGameStatus(game) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("owner_toggle_${game.id}")
                            ) {
                                Icon(
                                    imageVector = if (game.isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Status",
                                    tint = NeonGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (game.isActive) "Disable" else "Enable",
                                    color = NeonGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { gamePendingDelete = game },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonRed.copy(alpha = 0.16f))
                                    .testTag("owner_delete_${game.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Game",
                                    tint = NeonRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        OwnerGameEditorDialog(
            initialGame = editingGame,
            onDismiss = { showEditorDialog = false },
            onSave = { title, desc, cat, thumb, payload, creator, featured, gameType, apkPkg, apkVer, apkSize ->
                val target = editingGame
                if (target == null) {
                    onUploadNewGame(title, desc, cat, thumb, payload, creator, featured, gameType, apkPkg, apkVer, apkSize)
                } else {
                    onUpdateGame(target.id, title, desc, cat, thumb, payload, creator, featured, gameType, apkPkg, apkVer, apkSize)
                }
                showEditorDialog = false
            }
        )
    }



    if (gamePendingDelete != null) {
        val target = gamePendingDelete!!
        AlertDialog(
            onDismissRequest = { gamePendingDelete = null },
            containerColor = BloxSurface,
            title = {
                Text("Delete '${target.title}'?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This permanently removes '${target.title}' from Cloud Firestore and hides it for all users on all devices immediately.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGame(target)
                        gamePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gamePendingDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun OwnerGameEditorDialog(
    initialGame: Game?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String,
        featured: Boolean,
        gameType: String,
        apkPackageName: String,
        apkVersion: String,
        apkFileSize: Long
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedGameType by remember {
        mutableStateOf(if (initialGame?.isApkGame == true) "apk" else "html5")
    }
    var title by remember { mutableStateOf(initialGame?.title ?: "") }
    var description by remember { mutableStateOf(initialGame?.description ?: "") }
    var category by remember { mutableStateOf(initialGame?.category ?: "Obby") }
    var thumbnail by remember { mutableStateOf(initialGame?.thumbnail ?: "obby_neon") }
    var creator by remember { mutableStateOf(initialGame?.creator ?: "BloxVerse") }
    var featured by remember { mutableStateOf(initialGame?.featured ?: true) }
    var apkPackageName by remember { mutableStateOf(initialGame?.apkPackageName ?: "") }
    var apkVersion by remember { mutableStateOf(initialGame?.apkVersion ?: "1.0.0") }
    var apkFileSize by remember { mutableStateOf(initialGame?.apkFileSize ?: 0L) }
    var gameUrl by remember {
        mutableStateOf(
            if (initialGame != null) {
                if (initialGame.isApkGame) initialGame.gameUrl else GameZipExtractor.decompressIfNeeded(initialGame.gameUrl)
            } else ""
        )
    }
    var extractionSummary by remember { mutableStateOf<String?>(null) }
    var extractionError by remember { mutableStateOf<String?>(null) }

    // File Picker for HTML5 .zip, .html, .htm packages
    val htmlFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedGameType = "html5"
            val result = GameZipExtractor.extractFromUri(context, uri)
            result.onSuccess { bundle ->
                gameUrl = bundle.htmlContent
                if (title.isBlank()) {
                    title = bundle.suggestedTitle
                }
                extractionSummary = bundle.summary
                extractionError = null
            }.onFailure { err ->
                extractionError = err.localizedMessage ?: "Failed to read file"
                extractionSummary = null
            }
        }
    }

    // File Picker for Android Game APK (.apk)
    val apkFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedGameType = "apk"
            val result = GameZipExtractor.extractApkFromUri(context, uri)
            result.onSuccess { bundle ->
                gameUrl = bundle.apkPayload
                if (title.isBlank()) {
                    title = bundle.suggestedTitle
                }
                apkPackageName = bundle.packageName
                apkVersion = bundle.versionName
                apkFileSize = bundle.fileSize
                if (bundle.iconDataUrl != null) {
                    thumbnail = bundle.iconDataUrl
                }
                extractionSummary = bundle.summary
                extractionError = null
            }.onFailure { err ->
                extractionError = err.localizedMessage ?: "Failed to parse APK file"
                extractionSummary = null
            }
        }
    }

    // Image Picker for Custom Game Thumbnail
    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            GameZipExtractor.extractThumbnailDataUrl(context, uri)
                .onSuccess { dataUrl ->
                    thumbnail = dataUrl
                }
                .onFailure { err ->
                    extractionError = "Thumbnail error: ${err.localizedMessage}"
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BloxSurface,
        title = {
            Text(
                text = if (initialGame == null) "Upload New Game" else "Edit '${initialGame.title}'",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Game Type Switcher: [ HTML5 Web Game ] vs [ Android APK Game ]
                item {
                    Text(
                        text = "Game Format / Engine",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BloxSurfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { selectedGameType = "html5" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedGameType == "html5") NeonPurple else Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HTML5 / Web", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { selectedGameType = "apk" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedGameType == "apk") NeonCyan else Color.Transparent,
                                contentColor = if (selectedGameType == "apk") Color(0xFF042F2E) else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Android APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Step 1: Upload Source
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = BloxSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (selectedGameType == "apk") NeonCyan.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.6f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (selectedGameType == "apk") {
                                Text(
                                    text = "Step 1: Upload Android Game APK File (.apk)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Auto-extracts package ID, version, app icon, and payload.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { apkFilePickerLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("owner_pick_apk_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = Color(0xFF042F2E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select Game APK File", color = Color(0xFF042F2E), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            } else {
                                Text(
                                    text = "Step 1: Select Game File (.ZIP / .HTML / .JS) or Template",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { htmlFilePickerLauncher.launch("*/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("owner_pick_zip_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderZip,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upload ZIP / HTML", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val effectiveTitle = title.ifBlank { "Neon Arcade World" }
                                            if (title.isBlank()) title = effectiveTitle
                                            gameUrl = BuiltInHtml5Games.starterTemplateHtml(effectiveTitle)
                                            extractionSummary = "Loaded interactive HTML5 starter game"
                                            extractionError = null
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Template", color = NeonCyan, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (extractionSummary != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "✓ ${extractionSummary!!}",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (extractionError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⚠ ${extractionError!!}",
                                    color = NeonRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // If APK game: show package name & version inputs
                if (selectedGameType == "apk") {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = apkPackageName,
                                onValueChange = { apkPackageName = it },
                                label = { Text("Package Name", color = TextSecondary) },
                                placeholder = { Text("com.example.game", color = TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BloxCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("owner_apk_package_input")
                            )

                            OutlinedTextField(
                                value = apkVersion,
                                onValueChange = { apkVersion = it },
                                label = { Text("Version", color = TextSecondary) },
                                placeholder = { Text("1.0.0", color = TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BloxCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("owner_apk_version_input")
                            )
                        }
                    }
                }

                // 2. Game Title & Description
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Game Title", color = TextSecondary) },
                        placeholder = { Text("e.g. Cyber Parkour Obby", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_game_title_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & Controls", color = TextSecondary) },
                        placeholder = { Text("Describe how to play...", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_game_desc_input")
                    )
                }

                // 3. Category Selection
                item {
                    Text("Category", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BuiltInHtml5Games.CATEGORIES.filter { it != "All" }) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple,
                                    selectedLabelColor = Color.White,
                                    containerColor = BloxSurfaceVariant,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                // 4. Thumbnail Selection (Custom Image Upload OR Presets)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Game Thumbnail / Icon", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { thumbnailPickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Image", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BloxCardBorder, RoundedCornerShape(12.dp))
                    ) {
                        GameThumbnailBanner(
                            thumbnailKey = thumbnail,
                            category = category,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BuiltInHtml5Games.THUMBNAIL_PRESETS) { (key, label) ->
                            FilterChip(
                                selected = thumbnail == key,
                                onClick = { thumbnail = key },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = BloxSurfaceVariant,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                // 5. Direct URL / Payload
                item {
                    val labelText = if (selectedGameType == "apk") {
                        "Direct APK URL or Embedded Payload"
                    } else {
                        "Paste Game URL (https://...) or HTML5 Code"
                    }
                    val displayValue = when {
                        gameUrl.startsWith("apkbase64:") -> "[APK Binary Package Ready (${gameUrl.length / 1024} KB)]"
                        gameUrl.startsWith("gzbase64:") -> "[Compressed HTML5 ZIP Package Ready (${gameUrl.length / 1024} KB)]"
                        else -> gameUrl
                    }

                    OutlinedTextField(
                        value = displayValue,
                        onValueChange = {
                            if (!it.startsWith("[APK Binary") && !it.startsWith("[Compressed HTML5")) {
                                gameUrl = it
                            }
                        },
                        label = { Text(labelText, color = TextSecondary) },
                        placeholder = { Text(if (selectedGameType == "apk") "https://.../game.apk" else "https://... or <!DOCTYPE html>...", color = TextSecondary) },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_game_url_input")
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = featured,
                            onCheckedChange = { featured = it }
                        )
                        Text("Show in Featured Spotlight on Home", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedTitle = title.trim().ifBlank {
                        if (selectedGameType == "apk") "Android Game" else "BloxVerse World"
                    }
                    val resolvedDesc = description.trim().ifBlank {
                        "Play $resolvedTitle inside BloxVerse!"
                    }
                    val resolvedGamePayload = gameUrl.trim().ifBlank {
                        if (selectedGameType == "apk") "" else BuiltInHtml5Games.starterTemplateHtml(resolvedTitle)
                    }
                    val finalGameType = if (selectedGameType == "apk" || apkPackageName.isNotBlank() || gameUrl.startsWith("apkbase64:")) "apk" else "html5"
                    onSave(
                        resolvedTitle,
                        resolvedDesc,
                        category,
                        thumbnail,
                        resolvedGamePayload,
                        creator,
                        featured,
                        finalGameType,
                        apkPackageName.trim(),
                        apkVersion.trim(),
                        apkFileSize
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                modifier = Modifier.testTag("owner_publish_game_button")
            ) {
                Text(
                    text = if (initialGame == null) "Publish Game Live" else "Save Changes",
                    color = Color(0xFF042F2E),
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun OwnerMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(text = label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
