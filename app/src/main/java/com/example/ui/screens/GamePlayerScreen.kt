package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.BuiltInHtml5Games
import com.example.data.GameZipExtractor
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.ui.components.formatCountCompact
import com.example.ui.theme.BloxBackground
import com.example.ui.theme.BloxCardBorder
import com.example.ui.theme.BloxSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondary
import org.json.JSONObject

/**
 * Full-screen responsive HTML5 Game Player powered by Android WebView.
 *
 * Automatically restores and saves every player's:
 * - Current Level & Max Reached Level
 * - Latest Score & High Score
 * - Full HTML5 `window.localStorage` checkpoint state
 * both locally and to Cloud Firestore (`/game_progress/{id}`), surviving app uninstalls/reinstalls
 * and working seamlessly for all Play Store players.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamePlayerScreen(
    game: Game,
    savedProgress: GameProgress?,
    isFavorite: Boolean,
    isLiked: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
    onSaveProgress: (level: Int?, score: Long?, storageJson: String?, showToast: Boolean) -> Unit,
    onExitGame: () -> Unit
) {
    val context = LocalContext.current
    var reloadNonce by remember { mutableIntStateOf(0) }
    var hasRenderCrash by remember(game.id) { mutableStateOf(false) }
    var isHudCompact by remember { mutableStateOf(false) }
    var showCheckpointDialog by remember { mutableStateOf(false) }

    var liveLevel by remember(game.id, savedProgress?.maxLevel) {
        mutableIntStateOf(savedProgress?.maxLevel ?: 1)
    }
    var liveHighScore by remember(game.id, savedProgress?.highScore) {
        mutableLongStateOf(savedProgress?.highScore ?: 0L)
    }
    var latestStorageJson by remember(game.id) {
        mutableStateOf(savedProgress?.storageJson ?: "{}")
    }

    val currentOnSaveProgress by rememberUpdatedState(onSaveProgress)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember(game.id) {
        object {
            @JavascriptInterface
            fun saveProgress(level: Int, score: Long, storageJson: String?) {
                val cleanLevel = level.coerceIn(1, 100_000)
                val cleanScore = score.coerceAtLeast(0L)
                mainHandler.post {
                    if (cleanLevel > liveLevel) liveLevel = cleanLevel
                    if (cleanScore > liveHighScore) liveHighScore = cleanScore
                    if (!storageJson.isNullOrBlank()) {
                        latestStorageJson = storageJson
                    }
                    currentOnSaveProgress(cleanLevel, cleanScore, storageJson, false)
                }
            }

            @JavascriptInterface
            fun onStorageUpdated(storageJson: String) {
                mainHandler.post {
                    latestStorageJson = storageJson
                    val parsed = extractLevelAndScoreFromJson(storageJson, liveLevel, liveHighScore)
                    if (parsed.first > liveLevel) liveLevel = parsed.first
                    if (parsed.second > liveHighScore) liveHighScore = parsed.second
                    currentOnSaveProgress(parsed.first, parsed.second, storageJson, false)
                }
            }
        }
    }

    val initialStorageSeed = remember(game.id, reloadNonce) {
        savedProgress?.storageJson ?: "{}"
    }
    val initialLevelSeed = remember(game.id, reloadNonce) {
        savedProgress?.maxLevel ?: 1
    }
    val initialScoreSeed = remember(game.id, reloadNonce) {
        savedProgress?.highScore ?: 0L
    }

    val bootstrapJs = remember(game.id, initialStorageSeed, initialLevelSeed, initialScoreSeed) {
        buildPersistenceBootstrapJs(
            savedStorageJson = initialStorageSeed,
            savedLevel = initialLevelSeed,
            savedHighScore = initialScoreSeed
        )
    }

    val webView = remember(context, game.id, reloadNonce) {
        object : WebView(context) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                (parent as? View)?.let { parentView ->
                    if (parentView.id == View.NO_ID) {
                        parentView.id = com.example.R.id.game_player_holder
                    }
                    parentView.isSaveEnabled = false
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        parentView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    }
                }
            }

            override fun dispatchProvideStructure(structure: android.view.ViewStructure) {}
            override fun onProvideStructure(structure: android.view.ViewStructure) {}
            override fun dispatchProvideAutofillStructure(structure: android.view.ViewStructure, flags: Int) {}
            override fun onProvideAutofillStructure(structure: android.view.ViewStructure, flags: Int) {}
            override fun onProvideAutofillVirtualStructure(structure: android.view.ViewStructure, flags: Int) {}
        }.apply {
            id = com.example.R.id.game_player_webview
            isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#080B14"))
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
            }
            addJavascriptInterface(bridge, "BloxVerseBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    try {
                        (view?.parent as? ViewGroup)?.removeView(view)
                        view?.destroy()
                    } catch (_: Exception) {}
                    mainHandler.post {
                        hasRenderCrash = true
                    }
                    return true
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    fun flushAndExit() {
        currentOnSaveProgress(liveLevel, liveHighScore, latestStorageJson, false)
        try {
            webView.onPause()
            webView.stopLoading()
        } catch (_: Exception) {}
        onExitGame()
    }

    BackHandler {
        flushAndExit()
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try { webView.onPause() } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try { webView.onResume() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentOnSaveProgress(liveLevel, liveHighScore, latestStorageJson, false)
            try {
                webView.onPause()
                webView.stopLoading()
                webView.loadUrl("about:blank")
                (webView.parent as? ViewGroup)?.removeView(webView)
                mainHandler.postDelayed({
                    try {
                        webView.destroy()
                    } catch (_: Exception) {}
                }, 1000)
            } catch (_: Exception) {}
        }
    }

    val preparedHtmlPayload = remember(game.id, game.gameUrl, reloadNonce, bootstrapJs) {
        val raw = if (game.isApkGame) {
            GameZipExtractor.resolvePlayableHtmlForApk(context, game)
        } else {
            val uncompressed = GameZipExtractor.decompressIfNeeded(game.gameUrl)
            if (uncompressed.isBlank()) {
                BuiltInHtml5Games.starterTemplateHtml(game.title)
            } else {
                uncompressed
            }
        }
        injectBootstrapIntoHtml(raw, bootstrapJs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BloxBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (hasRenderCrash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    border = BorderStroke(1.dp, NeonPink),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("render_crash_recovery_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Game Engine Paused",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The game renderer encountered a temporary issue and was safely paused. You can reload the game or return to the lobby.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onExitGame() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("render_crash_exit_button")
                            ) {
                                Text("Exit Game", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    hasRenderCrash = false
                                    reloadNonce++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("render_crash_reload_button")
                            ) {
                                Text("Reload", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            AndroidView(
                factory = { webView },
            update = { view ->
                val tagKey = "${game.id}_${game.updatedAt?.seconds ?: 0L}_$reloadNonce"
                if (view.tag != tagKey) {
                    view.tag = tagKey
                    view.requestFocus()
                    if (game.isApkGame) {
                        view.loadDataWithBaseURL(
                            "https://appassets.androidplatform.net/games/${game.id}/",
                            preparedHtmlPayload,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } else {
                        val rawTrimmed = GameZipExtractor.decompressIfNeeded(game.gameUrl).trim()
                        val isDirectUrl = rawTrimmed.startsWith("http://", ignoreCase = true) ||
                            rawTrimmed.startsWith("https://", ignoreCase = true) ||
                            (rawTrimmed.startsWith("www.", ignoreCase = true) && !rawTrimmed.contains("<"))

                        if (isDirectUrl) {
                            val normalizedUrl = if (rawTrimmed.startsWith("http://", ignoreCase = true) ||
                                rawTrimmed.startsWith("https://", ignoreCase = true)
                            ) {
                                rawTrimmed
                            } else {
                                "https://$rawTrimmed"
                            }
                            view.loadUrl(normalizedUrl)
                        } else {
                            view.loadDataWithBaseURL(
                                "https://appassets.androidplatform.net/games/${game.id}/",
                                preparedHtmlPayload,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("html5_game_webview")
        )

        // Floating Neon Overlay Controls Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !isHudCompact) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(BloxSurface.copy(alpha = 0.90f))
                        .border(1.dp, NeonPurple, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Live Cloud-Saved Level & High Score Badge (Tap to view/update checkpoint)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(NeonPurple.copy(alpha = 0.25f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                            .clickable { showCheckpointDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("player_hud_progress_badge")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud Saved Progress",
                            tint = NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LVL $liveLevel • ★ ${formatCountCompact(liveHighScore)}",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Like Game",
                            tint = if (isLiked) NeonCyan else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite Game",
                            tint = if (isFavorite) NeonPink else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { reloadNonce++ },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Game",
                            tint = NeonCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    if (game.isApkGame) {
                        IconButton(
                            onClick = {
                                val result = GameZipExtractor.launchOrInstallApk(context, game)
                                result.onFailure {
                                    Toast.makeText(context, it.localizedMessage ?: "Could not open installer", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "Install or Launch APK",
                                tint = NeonCyan,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(BloxSurface.copy(alpha = 0.90f))
                    .border(1.dp, BloxCardBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isHudCompact = !isHudCompact },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isHudCompact) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle HUD",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { flushAndExit() },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(NeonPink.copy(alpha = 0.25f))
                        .border(1.dp, NeonPink.copy(alpha = 0.6f), CircleShape)
                        .testTag("exit_game_player_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Exit Game",
                        tint = NeonPink,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        }
    }

    if (showCheckpointDialog) {
        var levelInput by remember(liveLevel) { mutableStateOf(liveLevel.toString()) }
        var scoreInput by remember(liveHighScore) { mutableStateOf(liveHighScore.toString()) }

        AlertDialog(
            onDismissRequest = { showCheckpointDialog = false },
            containerColor = BloxSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = NeonGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cloud Level & Score Checkpoint",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your game progress, unlocked levels, and scores are automatically synced to Cloud Firestore so they remain safe even if you reinstall the app.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = levelInput,
                        onValueChange = { levelInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Current / Unlocked Level", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkpoint_level_input")
                    )
                    OutlinedTextField(
                        value = scoreInput,
                        onValueChange = { scoreInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Current / High Score", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkpoint_score_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newLvl = (levelInput.toIntOrNull() ?: liveLevel).coerceAtLeast(1)
                        val newScore = (scoreInput.toLongOrNull() ?: liveHighScore).coerceAtLeast(0L)
                        liveLevel = maxOf(liveLevel, newLvl)
                        liveHighScore = maxOf(liveHighScore, newScore)
                        if (!hasRenderCrash) {
                            try {
                                webView.evaluateJavascript(
                                    "try { localStorage.setItem('level', '$liveLevel'); localStorage.setItem('bloxverse_saved_level', '$liveLevel'); localStorage.setItem('score', '$liveHighScore'); localStorage.setItem('highScore', '$liveHighScore'); window.level = $liveLevel; window.score = $liveHighScore; } catch(e){}",
                                    null
                                )
                            } catch (_: Exception) {}
                        }
                        currentOnSaveProgress(liveLevel, liveHighScore, latestStorageJson, true)
                        showCheckpointDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.testTag("save_checkpoint_confirm_button")
                ) {
                    Text("Save to Cloud", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckpointDialog = false }) {
                    Text("Resume Game", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Extracts level and score numbers from an HTML5 game's `localStorage` JSON snapshot.
 */
internal fun extractLevelAndScoreFromJson(
    storageJson: String,
    fallbackLevel: Int = 1,
    fallbackScore: Long = 0L
): Pair<Int, Long> {
    var bestLevel = fallbackLevel.coerceAtLeast(1)
    var bestScore = fallbackScore.coerceAtLeast(0L)
    if (storageJson.isBlank() || storageJson == "{}") return bestLevel to bestScore

    try {
        val obj = JSONObject(storageJson)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val lowerKey = key.lowercase()
            val rawVal = obj.optString(key, "").trim()

            val numericVal = rawVal.toLongOrNull()
            if (numericVal != null) {
                if (lowerKey.contains("level") || lowerKey.contains("stage") || lowerKey.contains("wave") || lowerKey.contains("world") || lowerKey.contains("floor")) {
                    if (numericVal in 1..100_000) {
                        bestLevel = maxOf(bestLevel, numericVal.toInt())
                    }
                } else if (lowerKey.contains("score") || lowerKey.contains("high") || lowerKey.contains("best") || lowerKey.contains("point") || lowerKey.contains("coin") || lowerKey.contains("gold") || lowerKey.contains("gem") || lowerKey.contains("xp")) {
                    if (numericVal >= 0L) {
                        bestScore = maxOf(bestScore, numericVal)
                    }
                }
            } else if (rawVal.startsWith("{") && rawVal.endsWith("}")) {
                val nested = extractLevelAndScoreFromJson(rawVal, bestLevel, bestScore)
                bestLevel = maxOf(bestLevel, nested.first)
                bestScore = maxOf(bestScore, nested.second)
            }
        }
    } catch (_: Exception) {
    }
    return bestLevel to bestScore
}

/**
 * Builds the JavaScript persistence bootstrap that:
 * 1. Pre-hydrates `window.localStorage` from Cloud Firestore's saved `storageJson` before game scripts run.
 * 2. Hooks `localStorage.setItem` so every checkpoint/level/score saved by an HTML5 game is immediately
 *    forwarded to `window.BloxVerseBridge`.
 * 3. Periodically inspects `window` variables (`level`, `stage`, `score`, `highScore`, `coins`, etc.) and
 *    HUD text elements so even games that don't explicitly call `localStorage` have their levels and scores saved.
 */
private fun buildPersistenceBootstrapJs(
    savedStorageJson: String,
    savedLevel: Int,
    savedHighScore: Long
): String {
    val escapedJson = JSONObject.quote(savedStorageJson.ifBlank { "{}" })
    return """
(function() {
  if (window.__bloxPersistenceInitialized) return;
  window.__bloxPersistenceInitialized = true;

  var initialRaw = $escapedJson;
  var bestLevel = ${savedLevel.coerceAtLeast(1)};
  var bestScore = ${savedHighScore.coerceAtLeast(0L)};

  try {
    var parsed = JSON.parse(initialRaw);
    if (parsed && typeof parsed === 'object') {
      var origSet = Storage.prototype.setItem;
      Object.keys(parsed).forEach(function(k) {
        if (parsed[k] !== undefined && parsed[k] !== null) {
          origSet.call(window.localStorage, k, String(parsed[k]));
        }
      });
      if (!window.localStorage.getItem('bloxverse_saved_level')) {
        origSet.call(window.localStorage, 'bloxverse_saved_level', String(bestLevel));
      }
      if (!window.localStorage.getItem('bloxverse_high_score')) {
        origSet.call(window.localStorage, 'bloxverse_high_score', String(bestScore));
      }
    }
  } catch (e) {}

  function snapshotStorageJson() {
    var dump = {};
    try {
      for (var i = 0; i < window.localStorage.length; i++) {
        var k = window.localStorage.key(i);
        if (k) dump[k] = window.localStorage.getItem(k);
      }
    } catch (e) {}
    return JSON.stringify(dump);
  }

  function detectLevelAndScore() {
    var lvl = bestLevel;
    var sc = bestScore;
    try {
      var winLevelCand = [window.level, window.currentLevel, window.stage, window.currentStage, window.wave, window.world];
      for (var i = 0; i < winLevelCand.length; i++) {
        var v = parseInt(winLevelCand[i], 10);
        if (!isNaN(v) && v >= 1 && v <= 100000 && v > lvl) lvl = v;
      }
      var winScoreCand = [window.score, window.highScore, window.bestScore, window.points, window.coins, window.gold, window.xp];
      for (var j = 0; j < winScoreCand.length; j++) {
        var s = parseInt(winScoreCand[j], 10);
        if (!isNaN(s) && s >= 0 && s > sc) sc = s;
      }

      for (var kIdx = 0; kIdx < window.localStorage.length; kIdx++) {
        var key = window.localStorage.key(kIdx);
        if (!key) continue;
        var lk = key.toLowerCase();
        var val = parseInt(window.localStorage.getItem(key), 10);
        if (!isNaN(val)) {
          if ((lk.indexOf('level') !== -1 || lk.indexOf('stage') !== -1 || lk.indexOf('wave') !== -1) && val >= 1 && val <= 100000) {
            if (val > lvl) lvl = val;
          } else if ((lk.indexOf('score') !== -1 || lk.indexOf('best') !== -1 || lk.indexOf('high') !== -1 || lk.indexOf('point') !== -1 || lk.indexOf('coin') !== -1) && val >= 0) {
            if (val > sc) sc = val;
          }
        }
      }

      if (document && document.body) {
        var bodyText = (document.body.innerText || '').slice(0, 1500);
        var lvlMatch = bodyText.match(/(?:level|lvl|stage|wave)\s*[:\-]?\s*(\d{1,5})/i);
        if (lvlMatch && lvlMatch[1]) {
          var domLvl = parseInt(lvlMatch[1], 10);
          if (!isNaN(domLvl) && domLvl >= 1 && domLvl <= 100000 && domLvl > lvl) lvl = domLvl;
        }
        var scMatch = bodyText.match(/(?:score|points|coins|best|high\s*score)\s*[:\-]?\s*(\d{1,12})/i);
        if (scMatch && scMatch[1]) {
          var domSc = parseInt(scMatch[1], 10);
          if (!isNaN(domSc) && domSc >= 0 && domSc > sc) sc = domSc;
        }
      }
    } catch (e) {}
    return { level: lvl, score: sc };
  }

  var lastFlushTime = 0;
  function flushToBridge(force) {
    try {
      var now = Date.now();
      if (!force && (now - lastFlushTime < 2500)) return;
      var det = detectLevelAndScore();
      var storageStr = snapshotStorageJson();
      if (force || det.level > bestLevel || det.score > bestScore || storageStr !== initialRaw) {
        lastFlushTime = now;
        bestLevel = Math.max(bestLevel, det.level);
        bestScore = Math.max(bestScore, det.score);
        initialRaw = storageStr;
        if (window.BloxVerseBridge && window.BloxVerseBridge.saveProgress) {
          window.BloxVerseBridge.saveProgress(bestLevel, bestScore, storageStr);
        }
      }
    } catch (e) {}
  }

  window.__bloxFlushProgress = function() {
    flushToBridge(true);
  };

  window.saveBloxProgress = function(lvl, sc) {
    var changed = false;
    if (typeof lvl === 'number' && lvl > bestLevel) { bestLevel = Math.floor(lvl); changed = true; }
    if (typeof sc === 'number' && sc > bestScore) { bestScore = Math.floor(sc); changed = true; }
    if (changed) {
      flushToBridge(false);
    }
  };

  try {
    if (!window.__bloxStoragePatched) {
      window.__bloxStoragePatched = true;
      var nativeSetItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function(key, value) {
        nativeSetItem.apply(this, arguments);
        try {
          flushToBridge(false);
        } catch (e) {}
      };
    }
  } catch (e) {}

  setInterval(function() { flushToBridge(false); }, 3000);
  window.addEventListener('pagehide', function() {
    window.__bloxStopped = true;
    flushToBridge(true);
  });
  document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'hidden') flushToBridge(true);
  });
})();
""".trimIndent()
}

private fun injectBootstrapIntoHtml(rawHtml: String, bootstrapJs: String): String {
    val scriptTag = "<script>\n$bootstrapJs\n</script>"
    val headRegex = Regex("(<head[^>]*>)", RegexOption.IGNORE_CASE)
    return if (headRegex.containsMatchIn(rawHtml)) {
        headRegex.replaceFirst(rawHtml, "$1\n$scriptTag")
    } else {
        "$scriptTag\n$rawHtml"
    }
}
