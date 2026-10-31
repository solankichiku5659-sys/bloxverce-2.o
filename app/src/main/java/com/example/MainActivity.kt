package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextSecondary
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BloxLocalCache
import com.example.data.GameZipExtractor
import com.example.data.model.Game
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.BloxVerseRepository
import com.example.ui.screens.GameDetailBottomSheet
import com.example.ui.screens.GamePlayerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardsScreen
import com.example.ui.screens.OwnerPanelScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.BloxBackground
import com.example.ui.theme.BloxCardBorder
import com.example.ui.theme.BloxSurface
import com.example.ui.theme.BloxSurfaceVariant
import com.example.ui.theme.BloxVerseTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonGold
import com.example.ui.viewmodel.BloxVerseViewModel
import com.google.firebase.firestore.FirebaseFirestore
import android.content.res.Resources
import android.view.View
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var wrappedActivityResources: Resources? = null

    override fun getResources(): Resources {
        val base = super.getResources()
        if (wrappedActivityResources == null) {
            wrappedActivityResources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
                override fun getResourceName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceName(resid)
                }

                override fun getResourceEntryName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceEntryName(resid)
                }

                override fun getResourcePackageName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourcePackageName(resid)
                }

                override fun getResourceTypeName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceTypeName(resid)
                }
            }
        }
        return wrappedActivityResources ?: base
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appContext = LocalContext.current.applicationContext
            val persistentPlayerId = remember(appContext) {
                BloxLocalCache(appContext).resolvePersistentPlayerId()
            }
            BloxVerseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BloxVerseMainShell(
                        currentUserId = persistentPlayerId,
                        userDisplayName = "BloxPlayer"
                    )
                }
            }
        }
    }
}

enum class BloxDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Discover", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    LEADERBOARDS("Ranks", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    PROFILE("Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    OWNER("Owner", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

@Composable
fun BloxVerseMainShell(
    currentUserId: String,
    userDisplayName: String
) {
    // Resolves R.string.firestore_database_id and persistent BloxLocalCache at the factory boundary
    val viewModel: BloxVerseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val localCache = BloxLocalCache(app)
                BloxVerseViewModel(
                    repository = BloxVerseRepository(db = db, localCache = localCache),
                    currentUserId = currentUserId,
                    defaultUserName = userDisplayName
                )
            }
        }
    )

    val gamesState by viewModel.gamesState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val playerProgressMap by viewModel.playerProgressMap.collectAsStateWithLifecycle()
    val globalHighScores by viewModel.globalHighScores.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val detailGame by viewModel.selectedGameForDetail.collectAsStateWithLifecycle()
    val playingGame by viewModel.activePlayingGame.collectAsStateWithLifecycle()
    val activeReviews by viewModel.activeGameReviews.collectAsStateWithLifecycle()
    val isOwnerUnlocked by viewModel.isOwnerUnlocked.collectAsStateWithLifecycle()
    val showOwnerPasswordDialog by viewModel.showOwnerPasswordDialog.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf(BloxDestination.HOME) }
    var logoSecretTaps by remember { mutableIntStateOf(0) }

    // Auto-dismiss transient status banner after 3.5s
    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3500L)
            viewModel.clearBannerMessage()
        }
    }

    // Only games uploaded by the Owner are ever shown (zero default/pre-installed games)
    val allGames = when (val s = gamesState) {
        is UiState.Success -> s.data
        else -> emptyList()
    }

    val userProfile = when (val p = profileState) {
        is UiState.Success -> p.data
        else -> UserProfile(userId = currentUserId, username = userDisplayName)
    }

    val handlePlayGame: (Game) -> Unit = { game ->
        viewModel.launchGamePlayer(game)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playingGame != null) {
            val activeGame = allGames.find { it.id == playingGame!!.id } ?: playingGame!!
            val activeProgress = playerProgressMap[activeGame.id]

            GamePlayerScreen(
                game = activeGame,
                savedProgress = activeProgress,
                isFavorite = userProfile.favoriteSet.contains(activeGame.id),
                isLiked = userProfile.likedSet.contains(activeGame.id),
                onToggleFavorite = { viewModel.toggleFavorite(activeGame) },
                onToggleLike = { viewModel.toggleLike(activeGame) },
                onSaveProgress = { level, score, storageJson, showToast ->
                    viewModel.savePlayerGameProgress(
                        gameId = activeGame.id,
                        level = level,
                        score = score,
                        storageJson = storageJson,
                        showToast = showToast
                    )
                },
                onExitGame = {
                    viewModel.exitGamePlayer()
                }
            )
        } else {

    // Normal users only see HOME, LEADERBOARDS, PROFILE.
    // OWNER tab is strictly invisible unless unlocked via password "games".
    val visibleDestinations = remember(isOwnerUnlocked) {
        if (isOwnerUnlocked) {
            BloxDestination.entries
        } else {
            listOf(BloxDestination.HOME, BloxDestination.LEADERBOARDS, BloxDestination.PROFILE)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            containerColor = BloxBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                BloxTopAppBar(
                    isOwnerUnlocked = isOwnerUnlocked,
                    onLogoSecretTap = {
                        logoSecretTaps++
                        if (logoSecretTaps >= 5) {
                            logoSecretTaps = 0
                            viewModel.promptOwnerLogin()
                        }
                    },
                    onRefreshClick = { viewModel.refreshGamesFromCloud() }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = BloxSurface,
                        tonalElevation = 10.dp,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(BloxCardBorder.copy(alpha = 0.85f), Color.Transparent)
                                ),
                                shape = RectangleShape
                            )
                    ) {
                        visibleDestinations.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        letterSpacing = if (selected) 0.5.sp else 0.2.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonPurple.copy(alpha = 0.35f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = BloxSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, BloxCardBorder.copy(alpha = 0.85f))
                                ),
                                shape = RectangleShape
                            )
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        visibleDestinations.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonPurple.copy(alpha = 0.35f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (gamesState is UiState.Loading && allGames.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonPurple)
                        }
                    } else {
                        when (currentTab) {
                            BloxDestination.HOME -> {
                                HomeScreen(
                                    allGames = allGames,
                                    userProfile = userProfile,
                                    playerProgressMap = playerProgressMap,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    selectedSort = selectedSort,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onCategorySelect = viewModel::selectCategory,
                                    onSortSelect = viewModel::selectSort,
                                    onGameClick = viewModel::openGameDetail,
                                    onPlayGame = handlePlayGame,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRefreshGames = viewModel::refreshGamesFromCloud
                                )
                            }
                            BloxDestination.LEADERBOARDS -> {
                                LeaderboardsScreen(
                                    games = allGames,
                                    userProfile = userProfile,
                                    globalHighScores = globalHighScores,
                                    onGameClick = viewModel::openGameDetail,
                                    onPlayGame = handlePlayGame
                                )
                            }
                            BloxDestination.PROFILE -> {
                                ProfileScreen(
                                    userProfile = userProfile,
                                    allGames = allGames,
                                    playerProgressMap = playerProgressMap,
                                    onUpdateProfile = viewModel::updateProfile,
                                    onGameClick = viewModel::openGameDetail,
                                    onPlayGame = handlePlayGame,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onSecretOwnerTrigger = { viewModel.promptOwnerLogin() }
                                )
                            }
                            BloxDestination.OWNER -> {
                                if (isOwnerUnlocked) {
                                    OwnerPanelScreen(
                                        allGames = allGames,
                                        onUploadNewGame = viewModel::ownerUploadNewGame,
                                        onUpdateGame = viewModel::ownerUpdateGame,
                                        onMoveGamePosition = viewModel::ownerMoveGamePosition,
                                        onToggleGameStatus = viewModel::ownerToggleGameStatus,
                                        onDeleteGame = viewModel::ownerDeleteGame,
                                        onRefreshCatalog = viewModel::refreshGamesFromCloud,
                                        onPlayGame = handlePlayGame,
                                        onLockOwnerPanel = {
                                            viewModel.lockOwnerPanel()
                                            currentTab = BloxDestination.HOME
                                        }
                                    )
                                } else {
                                    currentTab = BloxDestination.HOME
                                }
                            }
                        }
                    }

                    // Transient Neon Status Toast Banner
                    androidx.compose.animation.AnimatedVisibility(
                        visible = bannerMessage != null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(BloxSurfaceVariant)
                                .border(1.dp, NeonCyan, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = bannerMessage ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Game Detail Modal Sheet
    if (detailGame != null) {
        val liveGame = allGames.find { it.id == detailGame!!.id } ?: detailGame!!
        GameDetailBottomSheet(
            game = liveGame,
            savedProgress = playerProgressMap[liveGame.id],
            isFavorite = userProfile.favoriteSet.contains(liveGame.id),
            isLiked = userProfile.likedSet.contains(liveGame.id),
            reviews = activeReviews,
            onDismiss = viewModel::closeGameDetail,
            onPlayClick = { handlePlayGame(liveGame) },
            onToggleFavorite = { viewModel.toggleFavorite(liveGame) },
            onToggleLike = { viewModel.toggleLike(liveGame) },
            onSubmitReview = { rating, comment ->
                viewModel.submitGameReview(liveGame, rating, comment)
            }
        )
    }

    // Hidden Owner Login Password Dialog (Password: "games")
    if (showOwnerPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissOwnerLogin,
            containerColor = BloxSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = NeonPink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Owner Vault Authentication", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the private Owner password to unlock game uploading, editing, and deletion controls.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Owner Password", color = TextSecondary) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPink,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyOwnerPassword(passwordInput) {
                            currentTab = BloxDestination.OWNER
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.testTag("owner_unlock_confirm_button")
                ) {
                    Text("Unlock Studio", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOwnerLogin) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    }
    }


}

@Composable
private fun BloxTopAppBar(
    isOwnerUnlocked: Boolean,
    onLogoSecretTap: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BloxSurface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, BloxCardBorder.copy(alpha = 0.6f))
                ),
                shape = RectangleShape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tapping the BloxVerse emblem 5 times is a secret owner gesture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogoSecretTap
                )
                .testTag("bloxverse_top_logo")
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(listOf(NeonCyan, NeonPurple)),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bloxverse_logo),
                    contentDescription = "BloxVerse Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BLOXVERSE",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    if (isOwnerUnlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonPink.copy(alpha = 0.25f))
                                .border(1.dp, NeonPink.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OWNER VAULT",
                                color = NeonPink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HTML5 & Android APK Universe",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BloxSurfaceVariant)
                    .border(1.dp, BloxCardBorder, CircleShape)
                    .testTag("top_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Games",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
