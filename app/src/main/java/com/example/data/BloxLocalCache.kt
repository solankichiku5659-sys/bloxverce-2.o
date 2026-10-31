package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent on-device cache backed by SharedPreferences and hardware `ANDROID_ID` so that:
 * 1. A deterministic device-scoped Player ID (`resolvePersistentPlayerId`) survives app uninstall and reinstall
 *    on Android 8.0+, allowing Cloud Firestore to automatically restore the player's saved levels, high scores,
 *    and HTML5 `localStorage` checkpoints after reinstalling the app.
 * 2. Games uploaded by the Owner and synced from Firestore persist across app restarts and refreshes
 *    in their exact `sortOrder` positions with zero flicker.
 * 3. Per-game progress (`GameProgress`: level, highScore, and HTML5 `localStorage` JSON) is cached locally
 *    and mirrored to Cloud Firestore.
 */
class BloxLocalCache(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns a stable Player ID derived from `Settings.Secure.ANDROID_ID` (which persists across app
     * uninstalls and reinstalls on Android devices signed with the same key).
     */
    @SuppressLint("HardwareIds")
    fun resolvePersistentPlayerId(): String {
        val cached = prefs.getString(KEY_DEVICE_PLAYER_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        val cleanId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "player_${androidId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}".take(64)
        } else {
            "player_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        }
        prefs.edit().putString(KEY_DEVICE_PLAYER_ID, cleanId).apply()
        return cleanId
    }

    fun saveGames(games: List<Game>) {
        val arr = JSONArray()
        val sorted = games.sortedWith(compareBy<Game> { it.sortOrder }.thenBy { it.id })
        for (g in sorted) {
            val obj = JSONObject().apply {
                put("id", g.id)
                put("platformId", g.platformId)
                put("title", g.title)
                put("description", g.description)
                put("thumbnail", g.thumbnail)
                put("gameUrl", g.gameUrl)
                put("category", g.category)
                put("creator", g.creator)
                put("plays", g.plays)
                put("likes", g.likes)
                put("rating", g.rating)
                put("ratingCount", g.ratingCount)
                put("status", g.status)
                put("featured", g.featured)
                put("sortOrder", g.sortOrder)
                put("ownerUid", g.ownerUid)
                put("createdAtSec", g.createdAt?.seconds ?: 0L)
                put("updatedAtSec", g.updatedAt?.seconds ?: 0L)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_GAMES_JSON, arr.toString()).apply()
    }

    fun loadGames(): List<Game> {
        val raw = prefs.getString(KEY_GAMES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Game>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val createdSec = obj.optLong("createdAtSec", 0L)
                val updatedSec = obj.optLong("updatedAtSec", 0L)
                list.add(
                    Game(
                        id = obj.optString("id"),
                        platformId = obj.optString("platformId", Game.LIVE_PLATFORM_ID),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        thumbnail = obj.optString("thumbnail", "obby_neon"),
                        gameUrl = obj.optString("gameUrl"),
                        category = obj.optString("category", "Obby"),
                        creator = obj.optString("creator", "BloxVerse"),
                        plays = obj.optLong("plays", 0L),
                        likes = obj.optLong("likes", 0L),
                        rating = obj.optDouble("rating", 5.0),
                        ratingCount = obj.optLong("ratingCount", 1L),
                        status = obj.optString("status", "active"),
                        featured = obj.optBoolean("featured", true),
                        sortOrder = obj.optLong("sortOrder", i.toLong() + 1L),
                        ownerUid = obj.optString("ownerUid", ""),
                        createdAt = if (createdSec > 0L) Timestamp(createdSec, 0) else Timestamp.now(),
                        updatedAt = if (updatedSec > 0L) Timestamp(updatedSec, 0) else Timestamp.now()
                    )
                )
            }
            list.sortedWith(compareBy<Game> { it.sortOrder }.thenBy { it.id })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveReassembledGamePayload(gameId: String, fullPayload: String) {
        prefs.edit().putString("reassembled_game_$gameId", fullPayload).apply()
    }

    fun loadReassembledGamePayload(gameId: String): String? {
        return prefs.getString("reassembled_game_$gameId", null)
    }

    fun saveUserProfile(profile: UserProfile) {
        val obj = JSONObject().apply {
            put("userId", profile.userId)
            put("username", profile.username)
            put("avatarKey", profile.avatarKey)
            put("bio", profile.bio)
            put("totalPlays", profile.totalPlays)
            put("coins", profile.coins)
            put("favoriteGameIds", profile.favoriteGameIds)
            put("likedGameIds", profile.likedGameIds)
            put("recentGameIds", profile.recentGameIds)
        }
        prefs.edit().putString(KEY_PROFILE_JSON, obj.toString()).apply()
    }

    fun loadUserProfile(defaultUserId: String, defaultUsername: String): UserProfile {
        val raw = prefs.getString(KEY_PROFILE_JSON, null)
            ?: return UserProfile(userId = defaultUserId, username = defaultUsername)
        return try {
            val obj = JSONObject(raw)
            UserProfile(
                userId = obj.optString("userId", defaultUserId),
                username = obj.optString("username", defaultUsername),
                avatarKey = obj.optString("avatarKey", "cyber_knight"),
                bio = obj.optString("bio", "Exploring the BloxVerse!"),
                totalPlays = obj.optLong("totalPlays", 0L),
                coins = obj.optLong("coins", 100L),
                favoriteGameIds = obj.optString("favoriteGameIds", ""),
                likedGameIds = obj.optString("likedGameIds", ""),
                recentGameIds = obj.optString("recentGameIds", ""),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        } catch (_: Exception) {
            UserProfile(userId = defaultUserId, username = defaultUsername)
        }
    }

    fun saveGameProgressMap(progressMap: Map<String, GameProgress>) {
        val arr = JSONArray()
        for (p in progressMap.values) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("gameId", p.gameId)
                put("userId", p.userId)
                put("userName", p.userName)
                put("currentLevel", p.currentLevel)
                put("maxLevel", p.maxLevel)
                put("lastScore", p.lastScore)
                put("highScore", p.highScore)
                put("storageJson", p.storageJson)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PROGRESS_JSON, arr.toString()).apply()
    }

    fun loadGameProgressMap(): Map<String, GameProgress> {
        val raw = prefs.getString(KEY_PROGRESS_JSON, null) ?: return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, GameProgress>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val gId = obj.optString("gameId")
                if (gId.isNotEmpty()) {
                    map[gId] = GameProgress(
                        id = obj.optString("id"),
                        gameId = gId,
                        userId = obj.optString("userId"),
                        userName = obj.optString("userName", "BloxPlayer"),
                        currentLevel = obj.optInt("currentLevel", 1).coerceAtLeast(1),
                        maxLevel = obj.optInt("maxLevel", 1).coerceAtLeast(1),
                        lastScore = obj.optLong("lastScore", 0L).coerceAtLeast(0L),
                        highScore = obj.optLong("highScore", 0L).coerceAtLeast(0L),
                        storageJson = obj.optString("storageJson", "{}"),
                        updatedAt = Timestamp.now()
                    )
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setOwnerUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_OWNER_UNLOCKED, unlocked).apply()
    }

    fun isOwnerUnlocked(): Boolean {
        return prefs.getBoolean(KEY_OWNER_UNLOCKED, false)
    }

    private companion object {
        const val PREFS_NAME = "bloxverse_persistent_store_v2"
        const val KEY_DEVICE_PLAYER_ID = "device_player_id_v2"
        const val KEY_GAMES_JSON = "cached_games_v2"
        const val KEY_PROFILE_JSON = "cached_profile_v2"
        const val KEY_PROGRESS_JSON = "cached_progress_v2"
        const val KEY_OWNER_UNLOCKED = "owner_unlocked_v2"
    }
}
