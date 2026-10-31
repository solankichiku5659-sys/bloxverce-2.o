package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.BloxLocalCache
import com.example.data.BuiltInHtml5Games
import com.example.data.GameZipExtractor
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.data.model.GameReview
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Primary Repository for BloxVerse.
 *
 * Guarantees:
 * 1. Zero pre-installed games: starts completely clean (`emptyList()`), showing ONLY games uploaded by the Owner.
 * 2. Real-time Multi-Device & Reinstall-Safe Cloud Firestore Sync: every game uploaded by the Owner is stored
 *    permanently in Cloud Firestore (`games` + `game_chunks` for large multi-megabyte bundles). Even if the
 *    Owner or any Play Store player deletes/uninstalls the app and reinstalls it, all uploaded games load
 *    immediately in their exact `sortOrder` positions.
 * 3. Universal Player Level, Score & HTML5 Checkpoint Cloud Sync (`game_progress`): every player's reached
 *    levels, high scores, and HTML5 `localStorage` checkpoints are saved both locally and in Cloud Firestore.
 */
class BloxVerseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val localCache: BloxLocalCache? = null,
    private val platformId: String = Game.LIVE_PLATFORM_ID
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        localCache = BloxLocalCache(context.applicationContext)
    )

    private val localGamesFlow = MutableStateFlow<List<Game>>(
        localCache?.loadGames().orEmpty()
    )

    private val defaultPlayerId: String = localCache?.resolvePersistentPlayerId() ?: LOCAL_PLAYER_ID

    private val localProfileFlow = MutableStateFlow(
        localCache?.loadUserProfile(defaultPlayerId, "BloxPlayer") ?: UserProfile(
            userId = defaultPlayerId,
            username = "BloxPlayer",
            avatarKey = "cyber_knight",
            bio = "Ready to conquer the BloxVerse!"
        )
    )

    private val localProgressMapFlow = MutableStateFlow<Map<String, GameProgress>>(
        localCache?.loadGameProgressMap().orEmpty()
    )

    private var ownerSessionUnlocked: Boolean = localCache?.isOwnerUnlocked() ?: false

    fun isOwnerCurrentlyUnlocked(): Boolean = ownerSessionUnlocked

    fun lockOwnerSession() {
        ownerSessionUnlocked = false
        localCache?.setOwnerUnlocked(false)
    }

    private fun resolveActiveOwnerId(): String {
        return OWNER_CLOUD_ADMIN_ID
    }

    fun resolveActivePlayerId(): String {
        return auth.currentUser?.uid ?: defaultPlayerId
    }

    private fun sortGamesFixed(games: List<Game>): List<Game> {
        return games.sortedWith(
            compareBy<Game> { if (it.sortOrder > 0L) it.sortOrder else Long.MAX_VALUE }
                .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )
    }

    /**
     * Resolves a game's full HTML5/APK payload if it was uploaded in multiple chunks (`chunked:N`).
     * Uses local cache first, otherwise downloads and joins all chunks from Cloud Firestore.
     */
    suspend fun ensureGamePayloadReady(game: Game): Game {
        if (!game.gameUrl.startsWith(CHUNKED_PREFIX)) return game
        val cachedPayload = localCache?.loadReassembledGamePayload(game.id)
        if (!cachedPayload.isNullOrBlank()) {
            return game.copy(gameUrl = cachedPayload)
        }
        val chunkCount = game.gameUrl.removePrefix(CHUNKED_PREFIX).toIntOrNull() ?: return game
        return try {
            val sb = StringBuilder()
            for (i in 0 until chunkCount) {
                val chunkDoc = db.collection("game_chunks")
                    .document("${game.id}_chunk_$i")
                    .get()
                    .await()
                val part = chunkDoc.getString("content") ?: ""
                sb.append(part)
            }
            val fullPayload = sb.toString()
            if (fullPayload.isNotBlank()) {
                localCache?.saveReassembledGamePayload(game.id, fullPayload)
                val fullGame = game.copy(gameUrl = fullPayload)
                val updatedList = localGamesFlow.value.map { if (it.id == game.id) fullGame else it }
                localGamesFlow.value = updatedList
                fullGame
            } else {
                game
            }
        } catch (_: Exception) {
            game
        }
    }

    /**
     * Real-time stream of all Owner-uploaded BloxVerse games from Cloud Firestore,
     * sorted deterministically by their fixed `sortOrder` position.
     */
    fun observeGames(): Flow<List<Game>> {
        val path = "games"
        return db.collection(path)
            .whereIn("platformId", listOf("bloxverse", "bloxverse_live_v2"))
            .snapshots()
            .map { snapshot ->
                val rawList = snapshot.documents.mapNotNull { Game.fromSnapshot(it) }
                val resolved = rawList.map { g ->
                    if (g.gameUrl.startsWith(CHUNKED_PREFIX)) {
                        val cached = localCache?.loadReassembledGamePayload(g.id)
                        if (!cached.isNullOrBlank()) g.copy(gameUrl = cached) else g
                    } else g
                }
                sortGamesFixed(resolved)
            }
            .onEach { cloudGames ->
                localGamesFlow.value = cloudGames
                localCache?.saveGames(cloudGames)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                if (localGamesFlow.value.isNotEmpty()) {
                    emit(sortGamesFixed(localGamesFlow.value))
                } else {
                    emit(emptyList())
                }
            }
    }

    /**
     * Fetches the current list of Owner-uploaded games from Cloud Firestore.
     */
    suspend fun getGames(): Result<List<Game>> {
        val path = "games"
        return try {
            val snapshot = db.collection(path)
                .whereIn("platformId", listOf("bloxverse", "bloxverse_live_v2"))
                .get()
                .await()
            val rawGames = snapshot.documents.mapNotNull { Game.fromSnapshot(it) }
            val resolved = rawGames.map { g ->
                if (g.gameUrl.startsWith(CHUNKED_PREFIX)) {
                    val cached = localCache?.loadReassembledGamePayload(g.id)
                    if (!cached.isNullOrBlank()) g.copy(gameUrl = cached) else g
                } else g
            }
            val games = sortGamesFixed(resolved)
            localGamesFlow.value = games
            localCache?.saveGames(games)
            Result.success(games)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            val cached = localGamesFlow.value
            if (cached.isNotEmpty()) {
                Result.success(sortGamesFixed(cached))
            } else {
                Result.failure(e)
            }
        }
    }

    // =========================================================================
    // PLAYER GAME PROGRESS: SAVED LEVELS, SCORES & HTML5 LOCALSTORAGE SYNC
    // =========================================================================

    /**
     * Real-time stream of all saved game progress (levels, high scores, and HTML5 checkpoints)
     * for the current player from Cloud Firestore (`game_progress`), merged with local cache.
     */
    fun observePlayerGameProgress(userId: String = resolveActivePlayerId()): Flow<Map<String, GameProgress>> {
        val path = "game_progress"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                val cloudMap = snapshot.documents
                    .mapNotNull { GameProgress.fromSnapshot(it) }
                    .associateBy { it.gameId }
                val merged = localProgressMapFlow.value.toMutableMap()
                for ((gameId, cloudProg) in cloudMap) {
                    val localProg = merged[gameId]
                    if (localProg == null) {
                        merged[gameId] = cloudProg
                    } else {
                        merged[gameId] = cloudProg.copy(
                            maxLevel = maxOf(localProg.maxLevel, cloudProg.maxLevel),
                            highScore = maxOf(localProg.highScore, cloudProg.highScore),
                            storageJson = if (cloudProg.storageJson.length >= localProg.storageJson.length) {
                                cloudProg.storageJson
                            } else {
                                localProg.storageJson
                            }
                        )
                    }
                }
                merged.toMap()
            }
            .onEach { merged ->
                localProgressMapFlow.value = merged
                localCache?.saveGameProgressMap(merged)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                emit(localProgressMapFlow.value)
            }
    }

    /**
     * Real-time stream of global high scores & max levels across all players in Cloud Firestore.
     */
    fun observeGlobalHighScores(): Flow<List<GameProgress>> {
        val path = "game_progress"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { GameProgress.fromSnapshot(it) }
                    .sortedWith(
                        compareByDescending<GameProgress> { it.highScore }
                            .thenByDescending { it.maxLevel }
                    )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                emit(localProgressMapFlow.value.values.toList())
            }
    }

    /**
     * Saves a player's level, score, and full HTML5 `localStorage` state for a game
     * to both local persistent storage and Cloud Firestore (`/game_progress/{progressId}`).
     */
    suspend fun saveGameProgress(
        gameId: String,
        userId: String = resolveActivePlayerId(),
        userName: String = localProfileFlow.value.username,
        level: Int? = null,
        score: Long? = null,
        storageJson: String? = null
    ): Result<GameProgress> {
        val existing = localProgressMapFlow.value[gameId]
        val cleanDocId = "${userId}_$gameId".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        val resolvedCurrentLevel = (level ?: existing?.currentLevel ?: 1).coerceIn(1, 100_000)
        val resolvedMaxLevel = maxOf(resolvedCurrentLevel, existing?.maxLevel ?: 1).coerceIn(1, 100_000)
        val resolvedLastScore = (score ?: existing?.lastScore ?: 0L).coerceAtLeast(0L)
        val resolvedHighScore = maxOf(resolvedLastScore, existing?.highScore ?: 0L).coerceAtLeast(0L)

        // Merge incoming localStorage JSON keys with existing saved keys so no checkpoint is ever lost
        val mergedStorageJson = mergeStorageJsonStrings(existing?.storageJson ?: "{}", storageJson)

        val updatedProgress = GameProgress(
            id = cleanDocId,
            gameId = gameId,
            userId = userId,
            userName = userName.trim().ifEmpty { "BloxPlayer" }.take(60),
            currentLevel = resolvedCurrentLevel,
            maxLevel = resolvedMaxLevel,
            lastScore = resolvedLastScore,
            highScore = resolvedHighScore,
            storageJson = mergedStorageJson,
            updatedAt = Timestamp.now()
        )

        val nextMap = localProgressMapFlow.value.toMutableMap().apply {
            put(gameId, updatedProgress)
        }
        localProgressMapFlow.value = nextMap
        localCache?.saveGameProgressMap(nextMap)

        val path = "game_progress/$cleanDocId"
        return try {
            db.collection("game_progress")
                .document(cleanDocId)
                .set(updatedProgress.toFirestoreMap())
                .await()
            Result.success(updatedProgress)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            // Even if offline, local progress is saved and will merge on next cloud sync
            Result.success(updatedProgress)
        }
    }

    private fun mergeStorageJsonStrings(oldJson: String, newJson: String?): String {
        if (newJson.isNullOrBlank()) return oldJson.ifBlank { "{}" }
        return try {
            val baseObj = try { JSONObject(oldJson) } catch (_: Exception) { JSONObject() }
            val incomingObj = JSONObject(newJson)
            val keys = incomingObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                baseObj.put(k, incomingObj.opt(k))
            }
            baseObj.toString().take(440_000)
        } catch (_: Exception) {
            oldJson.ifBlank { "{}" }
        }
    }

    // =========================================================================
    // USER PROFILE & INTERACTIONS
    // =========================================================================

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (auth.currentUser == null) {
            return localProfileFlow
        }
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot -> UserProfile.fromSnapshot(snapshot) }
            .onEach { profile ->
                if (profile != null) {
                    localProfileFlow.value = profile
                    localCache?.saveUserProfile(profile)
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        userId: String = resolveActivePlayerId(),
        defaultUsername: String = "BloxPlayer"
    ): Result<UserProfile> {
        if (auth.currentUser == null) {
            val current = localProfileFlow.value
            localCache?.saveUserProfile(current)
            return Result.success(current)
        }
        val path = "users/$userId"
        return try {
            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            val existingProfile = UserProfile.fromSnapshot(existing)
            if (existingProfile != null) {
                localProfileFlow.value = existingProfile
                localCache?.saveUserProfile(existingProfile)
                Result.success(existingProfile)
            } else {
                val cleanName = defaultUsername.trim().ifEmpty { "BloxPlayer" }.take(50)
                val newProfile = UserProfile(
                    userId = userId,
                    username = cleanName,
                    avatarKey = "cyber_knight",
                    bio = "Ready to conquer the BloxVerse!"
                )
                docRef.set(newProfile.toCreateMap()).await()
                localProfileFlow.value = newProfile
                localCache?.saveUserProfile(newProfile)
                Result.success(newProfile)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfileDetails(
        username: String,
        avatarKey: String,
        bio: String
    ): Result<Unit> {
        val updatedLocal = localProfileFlow.value.copy(
            username = username.trim().ifEmpty { "BloxPlayer" }.take(50),
            avatarKey = avatarKey.take(60),
            bio = bio.trim().take(300),
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedLocal
        localCache?.saveUserProfile(updatedLocal)

        val currentUser = auth.currentUser ?: return Result.success(Unit)
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            ensureUserProfile(uid, username)
            db.collection("users").document(uid).update(
                mapOf(
                    "username" to updatedLocal.username,
                    "avatarKey" to updatedLocal.avatarKey,
                    "bio" to updatedLocal.bio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun awardCoins(amount: Long): Result<Long> {
        val current = localProfileFlow.value
        val newCoins = (current.coins + amount).coerceAtLeast(0L)
        val updated = current.copy(
            coins = newCoins,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updated
        localCache?.saveUserProfile(updated)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            val path = "users/$uid"
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "coins" to FieldValue.increment(amount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, path)
            }
        }
        return Result.success(newCoins)
    }

    suspend fun toggleFavoriteGame(gameId: String, currentProfile: UserProfile?): Result<Boolean> {
        val profile = currentProfile ?: localProfileFlow.value
        val updatedSet = profile.favoriteSet.toMutableSet()
        val isNowFav = if (updatedSet.contains(gameId)) {
            updatedSet.remove(gameId)
            false
        } else {
            updatedSet.add(gameId)
            true
        }
        val csv = updatedSet.joinToString(",").take(5000)
        val newProfile = profile.copy(
            favoriteGameIds = csv,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = newProfile
        localCache?.saveUserProfile(newProfile)

        val currentUser = auth.currentUser ?: return Result.success(isNowFav)
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            ensureUserProfile(uid)
            db.collection("users").document(uid).update(
                mapOf(
                    "favoriteGameIds" to csv,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(isNowFav)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordGamePlay(game: Game, currentProfile: UserProfile?): Result<Unit> {
        val profile = currentProfile ?: localProfileFlow.value
        val recents = listOf(game.id) + profile.recentList.filter { it != game.id }
        val recentCsv = recents.take(20).joinToString(",").take(5000)
        val updatedProfile = profile.copy(
            totalPlays = profile.totalPlays + 1L,
            recentGameIds = recentCsv,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedProfile
        localCache?.saveUserProfile(updatedProfile)

        // Ensure an initial GameProgress entry exists for this game
        if (!localProgressMapFlow.value.containsKey(game.id)) {
            saveGameProgress(gameId = game.id, userName = updatedProfile.username)
        }

        val gamePath = "games/${game.id}"
        return try {
            db.collection("games").document(game.id).update(
                mapOf(
                    "plays" to (game.plays + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val currentUser = auth.currentUser
            if (currentUser != null) {
                ensureUserProfile(currentUser.uid)
                db.collection("users").document(currentUser.uid).update(
                    mapOf(
                        "totalPlays" to updatedProfile.totalPlays,
                        "recentGameIds" to recentCsv,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, gamePath)
            val updatedGames = localGamesFlow.value.map {
                if (it.id == game.id) it.copy(plays = it.plays + 1L) else it
            }
            localGamesFlow.value = updatedGames
            localCache?.saveGames(updatedGames)
            Result.success(Unit)
        }
    }

    suspend fun toggleLikeGame(game: Game, currentProfile: UserProfile?): Result<Boolean> {
        val profile = currentProfile ?: localProfileFlow.value
        val likedSet = profile.likedSet.toMutableSet()
        val isLikedNow: Boolean
        val newLikes: Long
        if (likedSet.contains(game.id)) {
            likedSet.remove(game.id)
            isLikedNow = false
            newLikes = (game.likes - 1L).coerceAtLeast(0L)
        } else {
            likedSet.add(game.id)
            isLikedNow = true
            newLikes = game.likes + 1L
        }

        val updatedProfile = profile.copy(
            likedGameIds = likedSet.joinToString(",").take(5000),
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedProfile
        localCache?.saveUserProfile(updatedProfile)

        val gamePath = "games/${game.id}"
        return try {
            db.collection("games").document(game.id).update(
                mapOf(
                    "likes" to newLikes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val currentUser = auth.currentUser
            if (currentUser != null) {
                ensureUserProfile(currentUser.uid)
                db.collection("users").document(currentUser.uid).update(
                    mapOf(
                        "likedGameIds" to updatedProfile.likedGameIds,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(isLikedNow)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, gamePath)
            Result.failure(e)
        }
    }

    fun observeReviewsForGame(gameId: String): Flow<List<GameReview>> {
        val path = "reviews"
        return db.collection(path)
            .whereEqualTo("status", "published")
            .whereEqualTo("gameId", gameId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { GameReview.fromSnapshot(it) }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
    }

    suspend fun submitReview(
        game: Game,
        userName: String,
        rating: Int,
        comment: String
    ): Result<String> {
        val coercedRating = rating.coerceIn(1, 5)
        val uid = resolveActivePlayerId()
        val reviewId = "rev_${game.id}_${uid}_${System.currentTimeMillis() / 1000}"
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(120)
        val reviewPath = "reviews/$reviewId"
        return try {
            val review = GameReview(
                id = reviewId,
                gameId = game.id,
                userId = uid,
                userName = userName.trim().ifEmpty { "BloxPlayer" }.take(60),
                rating = coercedRating,
                comment = comment.trim().ifEmpty { "Awesome game!" }.take(500),
                status = "published"
            )
            db.collection("reviews").document(reviewId).set(review.toCreateMap()).await()

            val newCount = game.ratingCount + 1L
            val newAvg = (((game.rating * game.ratingCount) + coercedRating) / newCount.toDouble())
                .coerceIn(0.0, 5.0)

            db.collection("games").document(game.id).update(
                mapOf(
                    "rating" to newAvg,
                    "ratingCount" to newCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(reviewId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reviewPath)
            Result.failure(e)
        }
    }

    // =========================================================================
    // OWNER SYSTEM (Protected by password "games")
    // =========================================================================

    suspend fun unlockOwnerPanel(passcode: String): Result<Boolean> {
        if (passcode.trim() != "games") {
            return Result.failure(IllegalArgumentException("Invalid Owner password."))
        }
        ownerSessionUnlocked = true
        localCache?.setOwnerUnlocked(true)
        val ownerId = resolveActiveOwnerId()
        return try {
            db.collection("admins").document(ownerId).set(
                mapOf(
                    "userId" to ownerId,
                    "passcode" to "games",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(true)
        } catch (e: Exception) {
            // In login-free mode, 'bloxverse_verified_owner' is also directly recognized by rules
            if (auth.currentUser == null) {
                Result.success(true)
            } else {
                handleFirestoreError(e, OperationType.CREATE, "admins/$ownerId")
                Result.failure(e)
            }
        }
    }

    private suspend fun uploadChunksIfNeeded(gameId: String, compressedPayload: String, ownerId: String): String {
        if (compressedPayload.length <= MAX_INLINE_GAME_CHARS) {
            return compressedPayload
        }
        val chunks = compressedPayload.chunked(CHUNK_SIZE_CHARS)
        for ((index, chunkText) in chunks.withIndex()) {
            db.collection("game_chunks")
                .document("${gameId}_chunk_$index")
                .set(
                    mapOf(
                        "gameId" to gameId,
                        "chunkIndex" to index,
                        "content" to chunkText,
                        "ownerUid" to ownerId
                    )
                )
                .await()
        }
        localCache?.saveReassembledGamePayload(gameId, compressedPayload)
        return "$CHUNKED_PREFIX${chunks.size}"
    }

    /**
     * Uploads a new game to Cloud Firestore and assigns it the next fixed `sortOrder` position.
     * Supports both single-document and multi-chunk large HTML5/ZIP games so they survive app
     * uninstall/reinstall and sync to all Play Store users.
     */
    suspend fun uploadGame(
        title: String,
        description: String,
        category: String,
        thumbnail: String,
        gameUrl: String,
        creator: String = "BloxVerse",
        featured: Boolean = true,
        customId: String? = null,
        gameType: String = "html5",
        apkPackageName: String = "",
        apkVersion: String = "",
        apkFileSize: Long = 0L
    ): Result<String> {
        if (!isOwnerCurrentlyUnlocked()) {
            return Result.failure(IllegalStateException("Owner privileges required. Unlock Owner Panel with password first."))
        }
        ownerSessionUnlocked = true
        val ownerId = resolveActiveOwnerId()

        val rawId = customId ?: "game_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        val gameId = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
        val validCategory = if (category in BuiltInHtml5Games.CATEGORIES && category != "All") {
            category
        } else {
            "Obby"
        }

        val currentList = localGamesFlow.value
        val nextSortOrder = (currentList.maxOfOrNull { it.sortOrder } ?: 0L) + 1L

        val resolvedGameType = if (gameType.equals("apk", ignoreCase = true) || apkPackageName.isNotBlank() || gameUrl.startsWith("apk:") || gameUrl.startsWith("apkbase64:")) "apk" else "html5"

        val compressedFullPayload = if (resolvedGameType == "apk") {
            gameUrl
        } else {
            GameZipExtractor.compressIfLarge(
                gameUrl.ifBlank { BuiltInHtml5Games.starterTemplateHtml(title.ifBlank { "BloxVerse World" }) }
            )
        }

        val path = "games/$gameId"
        return try {
            val firestoreGameUrlField = uploadChunksIfNeeded(gameId, compressedFullPayload, ownerId)

            val firestoreGame = Game(
                id = gameId,
                platformId = platformId,
                title = title.trim().ifEmpty { "Untitled Blox Game" }.take(100),
                description = description.trim().ifEmpty { "Play inside BloxVerse!" }.take(2000),
                thumbnail = thumbnail.trim().ifEmpty { "obby_neon" }.take(150000),
                gameUrl = firestoreGameUrlField,
                category = validCategory,
                creator = creator.trim().ifEmpty { "BloxVerse" }.take(100),
                plays = 0L,
                likes = 0L,
                rating = 5.0,
                ratingCount = 1L,
                status = "active",
                featured = featured,
                sortOrder = nextSortOrder,
                ownerUid = ownerId,
                gameType = resolvedGameType,
                apkPackageName = apkPackageName.trim().take(150),
                apkVersion = apkVersion.trim().take(50),
                apkFileSize = apkFileSize.coerceAtLeast(0L),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )

            db.collection("games").document(gameId).set(firestoreGame.toCreateMap(ownerId)).await()

            localCache?.saveReassembledGamePayload(gameId, compressedFullPayload)
            val hydratedLocalGame = firestoreGame.copy(gameUrl = compressedFullPayload)
            val updatedList = sortGamesFixed(localGamesFlow.value.filter { it.id != gameId } + hydratedLocalGame)
            localGamesFlow.value = updatedList
            localCache?.saveGames(updatedList)
            Result.success(gameId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGameDetails(
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
    ): Result<Unit> {
        if (!ownerSessionUnlocked) {
            return Result.failure(IllegalStateException("Owner privileges required."))
        }
        val ownerId = resolveActiveOwnerId()

        val validCategory = if (category in BuiltInHtml5Games.CATEGORIES && category != "All") {
            category
        } else {
            "Obby"
        }
        val isApk = (gameType != null && gameType.equals("apk", ignoreCase = true)) ||
                (apkPackageName != null && apkPackageName.isNotBlank()) ||
                gameUrl.startsWith("apk:") ||
                gameUrl.startsWith("apkbase64:")

        val compressedFullPayload = if (isApk) gameUrl else GameZipExtractor.compressIfLarge(gameUrl)

        val path = "games/$gameId"
        return try {
            val firestoreGameUrlField = uploadChunksIfNeeded(gameId, compressedFullPayload, ownerId)

            val updateMap = mutableMapOf<String, Any>(
                "title" to title.trim().ifEmpty { "Untitled Blox Game" }.take(100),
                "description" to description.trim().ifEmpty { "Play inside BloxVerse!" }.take(2000),
                "thumbnail" to thumbnail.trim().ifEmpty { "obby_neon" }.take(150000),
                "gameUrl" to firestoreGameUrlField,
                "category" to validCategory,
                "creator" to creator.trim().ifEmpty { "BloxVerse" }.take(100),
                "featured" to featured,
                "ownerUid" to ownerId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (gameType != null) updateMap["gameType"] = gameType
            if (apkPackageName != null) updateMap["apkPackageName"] = apkPackageName
            if (apkVersion != null) updateMap["apkVersion"] = apkVersion
            if (apkFileSize != null) updateMap["apkFileSize"] = apkFileSize

            db.collection("games").document(gameId).update(updateMap).await()

            val updatedList = localGamesFlow.value.map { g ->
                if (g.id == gameId) {
                    g.copy(
                        title = title.trim().ifEmpty { "Untitled Blox Game" }.take(100),
                        description = description.trim().ifEmpty { "Play inside BloxVerse!" }.take(2000),
                        thumbnail = thumbnail.trim().ifEmpty { "obby_neon" }.take(150000),
                        gameUrl = compressedFullPayload,
                        category = validCategory,
                        creator = creator.trim().ifEmpty { "BloxVerse" }.take(100),
                        featured = featured,
                        ownerUid = ownerId,
                        gameType = gameType ?: g.gameType,
                        apkPackageName = apkPackageName ?: g.apkPackageName,
                        apkVersion = apkVersion ?: g.apkVersion,
                        apkFileSize = apkFileSize ?: g.apkFileSize,
                        updatedAt = Timestamp.now()
                    )
                } else g
            }
            localGamesFlow.value = sortGamesFixed(updatedList)
            localCache?.saveGames(localGamesFlow.value)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun moveGamePosition(gameId: String, moveUp: Boolean): Result<Unit> {
        if (!ownerSessionUnlocked) {
            return Result.failure(IllegalStateException("Owner privileges required."))
        }
        val ownerId = resolveActiveOwnerId()

        val sorted = sortGamesFixed(localGamesFlow.value).toMutableList()
        val index = sorted.indexOfFirst { it.id == gameId }
        if (index == -1) return Result.success(Unit)
        val swapIndex = if (moveUp) index - 1 else index + 1
        if (swapIndex !in sorted.indices) return Result.success(Unit)

        val itemA = sorted[index]
        val itemB = sorted[swapIndex]
        sorted[index] = itemB
        sorted[swapIndex] = itemA

        val reindexed = sorted.mapIndexed { idx, g ->
            g.copy(sortOrder = (idx + 1).toLong(), updatedAt = Timestamp.now())
        }

        return try {
            for (g in reindexed) {
                db.collection("games").document(g.id).update(
                    mapOf(
                        "sortOrder" to g.sortOrder,
                        "ownerUid" to ownerId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            localGamesFlow.value = reindexed
            localCache?.saveGames(reindexed)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "games/$gameId")
            Result.failure(e)
        }
    }

    suspend fun setGameStatus(gameId: String, active: Boolean): Result<Unit> {
        if (!ownerSessionUnlocked) {
            return Result.failure(IllegalStateException("Owner privileges required."))
        }
        val ownerId = resolveActiveOwnerId()

        val path = "games/$gameId"
        return try {
            db.collection("games").document(gameId).update(
                mapOf(
                    "status" to if (active) "active" else "disabled",
                    "ownerUid" to ownerId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val updatedList = localGamesFlow.value.map { g ->
                if (g.id == gameId) g.copy(status = if (active) "active" else "disabled") else g
            }
            localGamesFlow.value = sortGamesFixed(updatedList)
            localCache?.saveGames(localGamesFlow.value)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGame(gameId: String): Result<Unit> {
        if (!ownerSessionUnlocked) {
            return Result.failure(IllegalStateException("Owner privileges required."))
        }
        val path = "games/$gameId"
        return try {
            db.collection("games").document(gameId).delete().await()
            val remaining = sortGamesFixed(localGamesFlow.value.filter { it.id != gameId })
            localGamesFlow.value = remaining
            localCache?.saveGames(remaining)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun launchOrInstallApkGame(context: Context, game: Game): Result<Boolean> {
        val readyGame = ensureGamePayloadReady(game)
        return GameZipExtractor.launchOrInstallApk(context, readyGame)
    }

    companion object {
        const val LOCAL_PLAYER_ID = "bloxverse_local_player"
        const val OWNER_CLOUD_ADMIN_ID = "bloxverse_verified_owner"
        const val CHUNKED_PREFIX = "chunked:"
        private const val MAX_INLINE_GAME_CHARS = 750_000
        private const val CHUNK_SIZE_CHARS = 400_000
    }
}
