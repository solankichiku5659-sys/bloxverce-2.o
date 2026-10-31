package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameZipExtractor
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.data.model.GameReview
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.BloxVerseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    POSITION("Fixed Order"),
    POPULAR("Most Played"),
    TOP_RATED("Top Rated"),
    NEWEST("Newest")
}

class BloxVerseViewModel(
    private val repository: BloxVerseRepository,
    private val currentUserId: String = repository.resolveActivePlayerId(),
    private val defaultUserName: String = "BloxPlayer"
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Default to POSITION ("Fixed Order") so games always remain in their exact uploaded positions
    private val _selectedSort = MutableStateFlow(SortOption.POSITION)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<Game?>(null)
    val selectedGameForDetail: StateFlow<Game?> = _selectedGameForDetail.asStateFlow()

    private val _activePlayingGame = MutableStateFlow<Game?>(null)
    val activePlayingGame: StateFlow<Game?> = _activePlayingGame.asStateFlow()

    // Hidden Owner System state: strictly false for normal users until unlocked with password "games"
    private val _isOwnerUnlocked = MutableStateFlow(repository.isOwnerCurrentlyUnlocked())
    val isOwnerUnlocked: StateFlow<Boolean> = _isOwnerUnlocked.asStateFlow()

    private val _showOwnerPasswordDialog = MutableStateFlow(false)
    val showOwnerPasswordDialog: StateFlow<Boolean> = _showOwnerPasswordDialog.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Real-time stream of Owner-uploaded games from Cloud Firestore (zero pre-installed fallback games)
    val gamesState: StateFlow<UiState<List<Game>>> = repository.observeGames()
        .map<List<Game>, UiState<List<Game>>> { cloudList ->
            UiState.Success(cloudList)
        }
        .catch { error ->
            Log.w(TAG, "Error observing games stream", error)
            emit(UiState.Error(error.message ?: "Failed to load games"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    // Real-time player profile state
    val userProfileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            UiState.Success(
                profile ?: UserProfile(
                    userId = currentUserId,
                    username = defaultUserName
                )
            )
        }
        .catch { error ->
            Log.w(TAG, "Error observing user profile", error)
            emit(UiState.Error(error.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    // Real-time map of saved player levels, high scores, and HTML5 checkpoints per gameId
    val playerProgressMap: StateFlow<Map<String, GameProgress>> = repository.observePlayerGameProgress(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    // Real-time global high scores & max levels across all players
    val globalHighScores: StateFlow<List<GameProgress>> = repository.observeGlobalHighScores()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    // Real-time reviews for currently inspected game
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGameReviews: StateFlow<List<GameReview>> = _selectedGameForDetail
        .flatMapLatest { game ->
            if (game == null) {
                flowOf(emptyList())
            } else {
                repository.observeReviewsForGame(game.id)
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, defaultUserName)
        }
    }

    fun refreshGamesFromCloud() {
        viewModelScope.launch {
            repository.getGames()
                .onSuccess {
                    _statusBannerMessage.value = "Synced live games catalog (${it.count { g -> g.isActive }} active)"
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Could not refresh catalog"
                }
        }
    }

    fun updateSearchQuery(query: String) {
        if (query.trim().equals("/owner", ignoreCase = true) ||
            query.trim().equals("#owner", ignoreCase = true)
        ) {
            _searchQuery.value = ""
            _showOwnerPasswordDialog.value = true
            return
        }
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectSort(sortOption: SortOption) {
        _selectedSort.value = sortOption
    }

    fun openGameDetail(game: Game) {
        _selectedGameForDetail.value = game
    }

    fun closeGameDetail() {
        _selectedGameForDetail.value = null
    }

    fun launchGamePlayer(game: Game) {
        _selectedGameForDetail.value = null
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.recordGamePlay(game, currentProfile)
            val readyGame = repository.ensureGamePayloadReady(game)
            _activePlayingGame.value = readyGame
        }
    }

    fun launchApkGame(context: Context, game: Game) {
        _selectedGameForDetail.value = null
        viewModelScope.launch {
            _statusBannerMessage.value = "Preparing '${game.title}'..."
            val currentProfile = (userProfileState.value as? UiState.Success)?.data
            repository.recordGamePlay(game, currentProfile)
            val readyGame = repository.ensureGamePayloadReady(game)
            val isAlreadyInstalled = GameZipExtractor.isAppInstalled(context, readyGame.apkPackageName)
            val result = GameZipExtractor.launchOrInstallApk(context, readyGame)
            result.onSuccess {
                _statusBannerMessage.value = if (isAlreadyInstalled) {
                    "Launched '${game.title}'"
                } else {
                    "Opening '${game.title}' installer..."
                }
            }.onFailure { err ->
                // If native APK installation is not permitted or fails, immediately launch the in-app player edition
                _statusBannerMessage.value = "Starting in-app playable edition of '${game.title}'..."
                _activePlayingGame.value = readyGame
            }
        }
    }

    fun recordGamePlayOnly(game: Game) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.recordGamePlay(game, currentProfile)
        }
    }

    fun exitGamePlayer() {
        _activePlayingGame.value = null
    }

    /**
     * Saves a player's current level, high score, and HTML5 `localStorage` JSON state
     * both locally and to Cloud Firestore (`/game_progress/{progressId}`).
     */
    fun savePlayerGameProgress(
        gameId: String,
        level: Int? = null,
        score: Long? = null,
        storageJson: String? = null,
        showToast: Boolean = false
    ) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        val userName = currentProfile?.username ?: defaultUserName
        viewModelScope.launch {
            repository.saveGameProgress(
                gameId = gameId,
                userId = currentUserId,
                userName = userName,
                level = level,
                score = score,
                storageJson = storageJson
            ).onSuccess { saved ->
                if (showToast) {
                    _statusBannerMessage.value = "Saved Level ${saved.maxLevel} • Best Score ${saved.highScore} to Cloud!"
                }
            }
        }
    }

    fun toggleFavorite(game: Game) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val result = repository.toggleFavoriteGame(game.id, currentProfile)
            result.onSuccess { isFav ->
                _statusBannerMessage.value = if (isFav) {
                    "Added '${game.title}' to Favorites"
                } else {
                    "Removed '${game.title}' from Favorites"
                }
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Could not update favorites"
            }
        }
    }

    fun toggleLike(game: Game) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.toggleLikeGame(game, currentProfile)
                .onSuccess { liked ->
                    _statusBannerMessage.value = if (liked) {
                        "Liked '${game.title}'!"
                    } else {
                        "Removed like from '${game.title}'"
                    }
                }
        }
    }

    fun submitGameReview(game: Game, rating: Int, comment: String) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        val userName = currentProfile?.username ?: defaultUserName
        viewModelScope.launch {
            repository.submitReview(game, userName, rating, comment)
                .onSuccess {
                    _statusBannerMessage.value = "Review published for '${game.title}'!"
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Could not publish review"
                }
        }
    }

    fun updateProfile(username: String, avatarKey: String, bio: String) {
        viewModelScope.launch {
            repository.updateUserProfileDetails(username, avatarKey, bio)
                .onSuccess {
                    _statusBannerMessage.value = "Profile updated!"
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Failed to update profile"
                }
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    // =========================================================================
    // HIDDEN OWNER SYSTEM ACTIONS (Password: "games")
    // =========================================================================

    fun promptOwnerLogin() {
        _showOwnerPasswordDialog.value = true
    }

    fun dismissOwnerLogin() {
        _showOwnerPasswordDialog.value = false
    }

    fun verifyOwnerPassword(password: String, onUnlocked: () -> Unit) {
        if (password.trim() != "games") {
            _statusBannerMessage.value = "Access Denied: Invalid Owner Password"
            return
        }
        viewModelScope.launch {
            val res = repository.unlockOwnerPanel("games")
            if (res.isSuccess) {
                _isOwnerUnlocked.value = true
                _showOwnerPasswordDialog.value = false
                _statusBannerMessage.value = "Owner Panel Unlocked • Cloud Admin Active"
                onUnlocked()
            } else {
                _statusBannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Owner unlock failed"
            }
        }
    }

    fun lockOwnerPanel() {
        repository.lockOwnerSession()
        _isOwnerUnlocked.value = false
        _statusBannerMessage.value = "Owner Panel Locked"
    }

    fun ownerUploadNewGame(
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String,
        featured: Boolean,
        gameType: String = "html5",
        apkPackageName: String = "",
        apkVersion: String = "",
        apkFileSize: Long = 0L
    ) {
        viewModelScope.launch {
            repository.uploadGame(
                title = title,
                description = description,
                category = category,
                thumbnail = thumbnail,
                gameUrl = gameUrl,
                creator = creator,
                featured = featured,
                gameType = gameType,
                apkPackageName = apkPackageName,
                apkVersion = apkVersion,
                apkFileSize = apkFileSize
            ).onSuccess {
                _statusBannerMessage.value = if (gameType.equals("apk", ignoreCase = true)) {
                    "Published APK game '$title' permanently to Cloud!"
                } else {
                    "Published HTML5 game '$title' permanently to Cloud!"
                }
            }.onFailure { e ->
                _statusBannerMessage.value = "Upload error: ${e.localizedMessage ?: "Failed to publish game"}"
            }
        }
    }

    fun ownerUpdateGame(
        gameId: String,
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String,
        featured: Boolean,
        gameType: String? = null,
        apkPackageName: String? = null,
        apkVersion: String? = null,
        apkFileSize: Long? = null
    ) {
        viewModelScope.launch {
            repository.updateGameDetails(
                gameId = gameId,
                title = title,
                description = description,
                category = category,
                thumbnail = thumbnail,
                gameUrl = gameUrl,
                creator = creator,
                featured = featured,
                gameType = gameType,
                apkPackageName = apkPackageName,
                apkVersion = apkVersion,
                apkFileSize = apkFileSize
            ).onSuccess {
                _statusBannerMessage.value = "Updated '$title' across all devices!"
            }.onFailure { e ->
                _statusBannerMessage.value = "Update error: ${e.localizedMessage ?: "Failed to update game"}"
            }
        }
    }

    fun ownerMoveGamePosition(game: Game, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveGamePosition(game.id, moveUp)
                .onSuccess {
                    _statusBannerMessage.value = "Updated fixed position for '${game.title}'"
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Could not reorder game"
                }
        }
    }

    fun ownerToggleGameStatus(game: Game) {
        val nextActive = !game.isActive
        viewModelScope.launch {
            repository.setGameStatus(game.id, nextActive)
                .onSuccess {
                    _statusBannerMessage.value = if (nextActive) {
                        "Enabled '${game.title}' for all users"
                    } else {
                        "Disabled '${game.title}' (hidden from users)"
                    }
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Failed to toggle status"
                }
        }
    }

    fun ownerDeleteGame(game: Game) {
        viewModelScope.launch {
            repository.deleteGame(game.id)
                .onSuccess {
                    _statusBannerMessage.value = "Deleted '${game.title}' permanently from BloxVerse"
                }
                .onFailure { e ->
                    _statusBannerMessage.value = e.localizedMessage ?: "Failed to delete game"
                }
        }
    }

    private companion object {
        const val TAG = "BloxVerseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
