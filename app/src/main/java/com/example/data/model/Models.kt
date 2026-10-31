package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Represents a game uploaded by the Owner on the BloxVerse platform.
 * Includes `sortOrder` so games always remain in their exact fixed position across refreshes and devices.
 */
data class Game(
    val id: String = "",
    val platformId: String = LIVE_PLATFORM_ID,
    val title: String = "",
    val description: String = "",
    val thumbnail: String = "obby_neon",
    val gameUrl: String = "",
    val category: String = "Obby",
    val creator: String = "BloxVerse",
    val plays: Long = 0L,
    val likes: Long = 0L,
    val rating: Double = 5.0,
    val ratingCount: Long = 1L,
    val status: String = "active",
    val featured: Boolean = true,
    val sortOrder: Long = 0L,
    val ownerUid: String = "",
    val gameType: String = "html5", // "html5" or "apk"
    val apkPackageName: String = "",
    val apkVersion: String = "",
    val apkFileSize: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isActive: Boolean
        get() = status == "active"

    val isApkGame: Boolean
        get() = gameType.equals("apk", ignoreCase = true) ||
                apkPackageName.isNotBlank() ||
                gameUrl.startsWith("apk:") ||
                gameUrl.endsWith(".apk", ignoreCase = true)

    fun toCreateMap(ownerId: String): Map<String, Any> {
        return mapOf(
            "id" to id,
            "platformId" to platformId,
            "title" to title.trim().take(100),
            "description" to description.trim().take(2000),
            "thumbnail" to thumbnail.trim().take(150000),
            "gameUrl" to gameUrl.take(900000),
            "category" to category,
            "creator" to creator.trim().ifEmpty { "BloxVerse" }.take(100),
            "plays" to plays.coerceAtLeast(0L),
            "likes" to likes.coerceAtLeast(0L),
            "rating" to rating.coerceIn(0.0, 5.0),
            "ratingCount" to ratingCount.coerceAtLeast(0L),
            "status" to status,
            "featured" to featured,
            "sortOrder" to sortOrder.coerceAtLeast(0L),
            "ownerUid" to ownerId,
            "gameType" to gameType,
            "apkPackageName" to apkPackageName,
            "apkVersion" to apkVersion,
            "apkFileSize" to apkFileSize,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        const val LIVE_PLATFORM_ID = "bloxverse_live_v2"

        fun fromSnapshot(doc: DocumentSnapshot): Game? {
            if (!doc.exists()) return null
            val id = doc.getString("id") ?: doc.id
            val title = doc.getString("title") ?: return null
            val createdTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val fallbackOrder = createdTs?.seconds ?: 0L
            val pkg = doc.getString("apkPackageName") ?: ""
            val gType = doc.getString("gameType") ?: if (pkg.isNotBlank()) "apk" else "html5"
            return Game(
                id = id,
                platformId = doc.getString("platformId") ?: LIVE_PLATFORM_ID,
                title = title,
                description = doc.getString("description") ?: "",
                thumbnail = doc.getString("thumbnail") ?: "obby_neon",
                gameUrl = doc.getString("gameUrl") ?: "",
                category = doc.getString("category") ?: "Obby",
                creator = doc.getString("creator") ?: "BloxVerse",
                plays = doc.getLong("plays") ?: 0L,
                likes = doc.getLong("likes") ?: 0L,
                rating = doc.getDouble("rating") ?: 5.0,
                ratingCount = doc.getLong("ratingCount") ?: 1L,
                status = doc.getString("status") ?: "active",
                featured = doc.getBoolean("featured") ?: true,
                sortOrder = doc.getLong("sortOrder") ?: fallbackOrder,
                ownerUid = doc.getString("ownerUid") ?: "",
                gameType = gType,
                apkPackageName = pkg,
                apkVersion = doc.getString("apkVersion") ?: "",
                apkFileSize = doc.getLong("apkFileSize") ?: 0L,
                createdAt = createdTs,
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Represents a player's saved progress (Levels, High Score, Recent Score, and HTML5 localStorage checkpoint)
 * for a specific game, persisted both on-device and in Cloud Firestore (`/game_progress/{id}`).
 */
data class GameProgress(
    val id: String = "",
    val gameId: String = "",
    val userId: String = "",
    val userName: String = "BloxPlayer",
    val currentLevel: Int = 1,
    val maxLevel: Int = 1,
    val lastScore: Long = 0L,
    val highScore: Long = 0L,
    val storageJson: String = "{}",
    val updatedAt: Timestamp? = null
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "gameId" to gameId,
            "userId" to userId,
            "userName" to userName.trim().ifEmpty { "BloxPlayer" }.take(60),
            "currentLevel" to currentLevel.coerceIn(1, 100_000),
            "maxLevel" to maxLevel.coerceIn(1, 100_000),
            "lastScore" to lastScore.coerceAtLeast(0L),
            "highScore" to highScore.coerceAtLeast(0L),
            "storageJson" to storageJson.ifBlank { "{}" }.take(450_000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): GameProgress? {
            if (!doc.exists()) return null
            val gameId = doc.getString("gameId") ?: return null
            val curLvl = (doc.getLong("currentLevel") ?: 1L).toInt().coerceAtLeast(1)
            val maxLvl = (doc.getLong("maxLevel") ?: curLvl.toLong()).toInt().coerceAtLeast(curLvl)
            val lastSc = (doc.getLong("lastScore") ?: 0L).coerceAtLeast(0L)
            val highSc = (doc.getLong("highScore") ?: lastSc).coerceAtLeast(lastSc)
            return GameProgress(
                id = doc.getString("id") ?: doc.id,
                gameId = gameId,
                userId = doc.getString("userId") ?: "",
                userName = doc.getString("userName") ?: "BloxPlayer",
                currentLevel = curLvl,
                maxLevel = maxLvl,
                lastScore = lastSc,
                highScore = highSc,
                storageJson = doc.getString("storageJson") ?: "{}",
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Represents a BloxVerse user's profile and personal game lists.
 */
data class UserProfile(
    val userId: String = "",
    val username: String = "BloxPlayer",
    val avatarKey: String = "cyber_knight",
    val bio: String = "Exploring the BloxVerse!",
    val totalPlays: Long = 0L,
    val coins: Long = 100L,
    val favoriteGameIds: String = "",
    val likedGameIds: String = "",
    val recentGameIds: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val favoriteSet: Set<String>
        get() = favoriteGameIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val likedSet: Set<String>
        get() = likedGameIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val recentList: List<String>
        get() = recentGameIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "username" to username.trim().ifEmpty { "BloxPlayer" }.take(50),
            "avatarKey" to avatarKey.take(60),
            "bio" to bio.trim().take(300),
            "totalPlays" to totalPlays.coerceAtLeast(0L),
            "coins" to coins.coerceAtLeast(0L),
            "favoriteGameIds" to favoriteGameIds.take(5000),
            "likedGameIds" to likedGameIds.take(5000),
            "recentGameIds" to recentGameIds.take(5000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): UserProfile? {
            if (!doc.exists()) return null
            val uid = doc.getString("userId") ?: doc.id
            return UserProfile(
                userId = uid,
                username = doc.getString("username") ?: "BloxPlayer",
                avatarKey = doc.getString("avatarKey") ?: "cyber_knight",
                bio = doc.getString("bio") ?: "Exploring the BloxVerse!",
                totalPlays = doc.getLong("totalPlays") ?: 0L,
                coins = doc.getLong("coins") ?: 100L,
                favoriteGameIds = doc.getString("favoriteGameIds") ?: "",
                likedGameIds = doc.getString("likedGameIds") ?: "",
                recentGameIds = doc.getString("recentGameIds") ?: "",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Represents a user rating and review for a game.
 */
data class GameReview(
    val id: String = "",
    val gameId: String = "",
    val userId: String = "",
    val userName: String = "BloxPlayer",
    val rating: Int = 5,
    val comment: String = "",
    val status: String = "published",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "gameId" to gameId,
            "userId" to userId,
            "userName" to userName.trim().ifEmpty { "BloxPlayer" }.take(60),
            "rating" to rating.coerceIn(1, 5),
            "comment" to comment.trim().ifEmpty { "Great game!" }.take(500),
            "status" to "published",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): GameReview? {
            if (!doc.exists()) return null
            return GameReview(
                id = doc.getString("id") ?: doc.id,
                gameId = doc.getString("gameId") ?: "",
                userId = doc.getString("userId") ?: "",
                userName = doc.getString("userName") ?: "BloxPlayer",
                rating = (doc.getLong("rating") ?: 5L).toInt(),
                comment = doc.getString("comment") ?: "",
                status = doc.getString("status") ?: "published",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Standard UI state wrapper for reactive Flows.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
