package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BloxVerseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerUploadMultipleGames_preservesExactFixedPositionsAcrossDevicesAndRefreshes() = runBlocking {
        val testPlatformId = "bloxverse_live_v2"
        auth.signOut()

        // Device 1 (Owner Device - login-free, unlocked with password "games")
        val ownerRepo = BloxVerseRepository(firestore, auth, localCache = null, platformId = testPlatformId)
        val unlockResult = withTimeout(TIMEOUT_MS) {
            ownerRepo.unlockOwnerPanel("games")
        }
        assertTrue("Owner unlock with 'games' should succeed", unlockResult.isSuccess)

        val id1 = "game_pos1_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val id2 = "game_pos2_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val up1 = withTimeout(TIMEOUT_MS) {
            ownerRepo.uploadGame(
                title = "First Owner Game",
                description = "Uploaded at position 1",
                category = "Obby",
                thumbnail = "obby_neon",
                gameUrl = "<html><body>Game 1</body></html>",
                customId = id1
            )
        }
        assertTrue("First game upload must succeed", up1.isSuccess)

        val up2 = withTimeout(TIMEOUT_MS) {
            ownerRepo.uploadGame(
                title = "Second Owner Game",
                description = "Uploaded at position 2",
                category = "Action",
                thumbnail = "star_raider",
                gameUrl = "<html><body>Game 2</body></html>",
                customId = id2
            )
        }
        assertTrue("Second game upload must succeed", up2.isSuccess)

        // Device 2 (Another Player Device on login-free session) observes Cloud Firestore live
        val playerDeviceRepo = BloxVerseRepository(firestore, auth, localCache = null, platformId = testPlatformId)
        val syncedGames = withTimeout(FLOW_TIMEOUT_MS) {
            playerDeviceRepo.observeGames().first { list ->
                list.any { it.id == id1 } && list.any { it.id == id2 }
            }
        }

        val pos1Index = syncedGames.indexOfFirst { it.id == id1 }
        val pos2Index = syncedGames.indexOfFirst { it.id == id2 }
        assertTrue("First uploaded game must appear before second uploaded game", pos1Index < pos2Index)

        // Playing or liking the second game must NOT change their fixed positions on refresh
        val game2 = syncedGames[pos2Index]
        withTimeout(TIMEOUT_MS) {
            playerDeviceRepo.recordGamePlay(game2, null)
            playerDeviceRepo.toggleLikeGame(game2, null)
        }

        val refreshedGames = withTimeout(TIMEOUT_MS) {
            playerDeviceRepo.getGames().getOrThrow()
        }
        val refreshedIndex1 = refreshedGames.indexOfFirst { it.id == id1 }
        val refreshedIndex2 = refreshedGames.indexOfFirst { it.id == id2 }
        assertTrue("Games must remain in their exact fixed positions after play/like and refresh", refreshedIndex1 < refreshedIndex2)

        // Clean up by deleting as Owner
        assertTrue(withTimeout(TIMEOUT_MS) { ownerRepo.deleteGame(id1) }.isSuccess)
        assertTrue(withTimeout(TIMEOUT_MS) { ownerRepo.deleteGame(id2) }.isSuccess)
    }

    @Test
    fun reinstallAndMultiDevice_restoresUploadedGamesAndPlayerLevelsAndScores() = runBlocking {
        val testPlatformId = "bloxverse_live_v2"
        auth.signOut()

        // 1. Owner uploads a game
        val ownerRepo = BloxVerseRepository(firestore, auth, localCache = null, platformId = testPlatformId)
        assertTrue(withTimeout(TIMEOUT_MS) { ownerRepo.unlockOwnerPanel("games") }.isSuccess)

        val gameId = "reinstall_game_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val uploadRes = withTimeout(TIMEOUT_MS) {
            ownerRepo.uploadGame(
                title = "Cyber Obby Reinstall Test",
                description = "Persists across app uninstall and reinstall",
                category = "Obby",
                thumbnail = "obby_neon",
                gameUrl = "<html><body><h1>Level 1</h1></body></html>",
                customId = gameId
            )
        }
        assertTrue("Owner game upload must succeed", uploadRes.isSuccess)

        // 2. Play Store Player plays the game and reaches Level 7 with High Score 4500
        val persistentDevicePlayerId = "player_device_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val playerSessionBeforeUninstall = BloxVerseRepository(firestore, auth, localCache = null, platformId = testPlatformId)
        val saveProgRes = withTimeout(TIMEOUT_MS) {
            playerSessionBeforeUninstall.saveGameProgress(
                gameId = gameId,
                userId = persistentDevicePlayerId,
                userName = "PlayStoreGamer",
                level = 7,
                score = 4500L,
                storageJson = """{"level":"7","highScore":"4500","unlockedStage":"7"}"""
            )
        }
        assertTrue("Saving player level and score to Cloud Firestore must succeed", saveProgRes.isSuccess)

        // 3. Simulate deleting/uninstalling the app and reinstalling from Play Store (brand new repository, empty local cache)
        val freshReinstallRepo = BloxVerseRepository(firestore, auth, localCache = null, platformId = testPlatformId)

        val restoredGames = withTimeout(TIMEOUT_MS) {
            freshReinstallRepo.getGames().getOrThrow()
        }
        assertTrue("Uploaded game must still exist after app uninstall and reinstall", restoredGames.any { it.id == gameId })

        val restoredProgressMap = withTimeout(FLOW_TIMEOUT_MS) {
            freshReinstallRepo.observePlayerGameProgress(persistentDevicePlayerId).first { map ->
                map.containsKey(gameId)
            }
        }
        val restoredProgress = restoredProgressMap[gameId]
        assertNotNull("Player progress must be restored from Cloud Firestore after reinstall", restoredProgress)
        assertEquals(7, restoredProgress!!.maxLevel)
        assertEquals(4500L, restoredProgress.highScore)
        assertTrue(restoredProgress.storageJson.contains("\"unlockedStage\":\"7\""))

        // Clean up
        assertTrue(withTimeout(TIMEOUT_MS) { ownerRepo.deleteGame(gameId) }.isSuccess)
    }

    @Test
    fun normalUser_cannotUploadOrDeleteGames_onlyOwnerCan() = runBlocking {
        val normalEmail = "player_${UUID.randomUUID().toString().take(8)}@bloxverse.test"
        signInTestUser(normalEmail)
        val normalRepo = BloxVerseRepository(firestore, auth)

        val unauthorizedUpload = withTimeout(TIMEOUT_MS) {
            normalRepo.uploadGame(
                title = "Unauthorized Game",
                description = "Should be denied by Firestore rules",
                category = "Obby",
                thumbnail = "obby_neon",
                gameUrl = "<html></html>"
            )
        }
        assertTrue("Normal user without Owner privilege must not be able to upload games", unauthorizedUpload.isFailure)
    }

    private fun extractFirestoreException(t: Throwable): FirebaseFirestoreException? {
        var curr: Throwable? = t
        while (curr != null) {
            if (curr is FirebaseFirestoreException) return curr
            curr = curr.cause
        }
        return null
    }

    @Test
    fun crossUserAccess_cannotReadAnotherUsersProfile() = runBlocking {
        val aliceEmail = "alice_iso_${UUID.randomUUID().toString().take(8)}@bloxverse.test"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = BloxVerseRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(aliceUid, "AliceSecret")
        }

        val bobEmail = "bob_iso_${UUID.randomUUID().toString().take(8)}@bloxverse.test"
        signInTestUser(bobEmail)
        val bobRepo = BloxVerseRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeUserProfile(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when reading another user's profile")
        } catch (t: Throwable) {
            val fsEx = extractFirestoreException(t)
            assertNotNull("Expected FirebaseFirestoreException in cause chain, got $t", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 10000L
    }
}
