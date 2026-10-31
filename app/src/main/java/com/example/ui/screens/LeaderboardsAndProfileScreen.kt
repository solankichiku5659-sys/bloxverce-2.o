package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.data.model.UserProfile
import com.example.ui.components.BloxGameCard
import com.example.ui.components.formatCountCompact
import com.example.ui.theme.BloxCardBorder
import com.example.ui.theme.BloxSurface
import com.example.ui.theme.BloxSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun LeaderboardsScreen(
    games: List<Game>,
    userProfile: UserProfile,
    globalHighScores: List<GameProgress> = emptyList(),
    onGameClick: (Game) -> Unit,
    onPlayGame: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGames = games.filter { it.isActive }
    val activeGameMap = remember(activeGames) { activeGames.associateBy { it.id } }
    // 0 = Most Played, 1 = Highest Rated, 2 = Most Liked, 3 = Player High Scores & Levels
    var boardMode by remember { mutableIntStateOf(0) }

    val rankedGames = when (boardMode) {
        0 -> activeGames.sortedByDescending { it.plays }
        1 -> activeGames.sortedByDescending { it.rating }
        else -> activeGames.sortedByDescending { it.likes }
    }

    val validPlayerScores = remember(globalHighScores, activeGameMap) {
        globalHighScores.filter { activeGameMap.containsKey(it.gameId) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = BloxSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(NeonGold, NeonPurple, NeonCyan)),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(NeonGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Hall of Fame",
                            tint = NeonGold,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BloxVerse Global Leaderboards",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Your Rank: ${
                                when {
                                    userProfile.totalPlays >= 25 -> "Grandmaster Bloxxer"
                                    userProfile.totalPlays >= 10 -> "Cyber Veteran"
                                    userProfile.totalPlays >= 3 -> "Neon Explorer"
                                    else -> "Rookie Challenger"
                                }
                            } (${userProfile.totalPlays} sessions)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("Most Played", "Highest Rated", "Most Liked", "Player Scores & Levels")
                itemsIndexed(tabs) { index, label ->
                    val selected = boardMode == index
                    FilterChip(
                        selected = selected,
                        onClick = { boardMode = index },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = Color.White,
                            containerColor = BloxSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        if (boardMode == 3) {
            if (validPlayerScores.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = BloxSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Play any game to record your cloud-saved levels and high scores here!",
                            color = TextSecondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(validPlayerScores, key = { _, p -> p.id }) { index, prog ->
                    val rank = index + 1
                    val game = activeGameMap[prog.gameId]
                    val medalColor = when (rank) {
                        1 -> NeonGold
                        2 -> Color(0xFFE2E8F0)
                        3 -> Color(0xFFF59E0B)
                        else -> NeonCyan
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = BloxSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BloxCardBorder, RoundedCornerShape(18.dp))
                            .clickable { if (game != null) onGameClick(game) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(medalColor.copy(alpha = 0.2f))
                                    .border(1.5.dp, medalColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$rank",
                                    color = medalColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prog.userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = game?.title ?: "BloxVerse World",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "LEVEL ${prog.maxLevel}",
                                        color = NeonGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "SCORE ${formatCountCompact(prog.highScore)}",
                                        color = NeonGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            if (game != null) {
                                IconButton(
                                    onClick = { onPlayGame(game) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(NeonPurple)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play ${game.title}",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(rankedGames, key = { _, g -> g.id }) { index, game ->
                val rank = index + 1
                val medalColor = when (rank) {
                    1 -> NeonGold
                    2 -> Color(0xFFE2E8F0)
                    3 -> Color(0xFFF59E0B)
                    else -> NeonPurple
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BloxCardBorder, RoundedCornerShape(18.dp))
                        .clickable { onGameClick(game) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(medalColor.copy(alpha = 0.2f))
                                .border(1.5.dp, medalColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$rank",
                                color = medalColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${game.category} • by ${game.creator}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "▶ ${formatCountCompact(game.plays)} plays",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "★ ${String.format(Locale.US, "%.1f", game.rating)}",
                                    color = NeonGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "♥ ${formatCountCompact(game.likes)}",
                                    color = NeonPink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { onPlayGame(game) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(NeonPurple)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play ${game.title}",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    allGames: List<Game>,
    playerProgressMap: Map<String, GameProgress> = emptyMap(),
    onUpdateProfile: (String, String, String) -> Unit,
    onGameClick: (Game) -> Unit,
    onPlayGame: (Game) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onSecretOwnerTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGames = allGames.filter { it.isActive }
    val favoriteGames = activeGames.filter { userProfile.favoriteSet.contains(it.id) }
    val recentGames = userProfile.recentList.mapNotNull { id -> activeGames.find { it.id == id } }
    val savedProgressEntries = activeGames.mapNotNull { g ->
        playerProgressMap[g.id]?.let { g to it }
    }
    val context = LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var secretTapCount by remember { mutableIntStateOf(0) }

    val avatarColors = mapOf(
        "cyber_knight" to (NeonPurple to "Cyber Knight"),
        "neon_ninja" to (NeonCyan to "Neon Ninja"),
        "plasma_pilot" to (NeonPink to "Plasma Pilot"),
        "emerald_guardian" to (NeonGreen to "Emerald Guardian")
    )
    val currentAvatarColor = avatarColors[userProfile.avatarKey]?.first ?: NeonPurple

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Player Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BloxSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(NeonPurple, NeonCyan)),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(currentAvatarColor, NeonCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "Player Avatar",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.username,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = userProfile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BloxSurfaceVariant)
                                .testTag("edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Player Stats Row
                    val highestLevelAcrossGames = playerProgressMap.values.maxOfOrNull { it.maxLevel } ?: 1
                    val totalHighScoreSum = playerProgressMap.values.sumOf { it.highScore }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BloxSurfaceVariant)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatBadge(
                            value = formatCountCompact(userProfile.totalPlays),
                            label = "Plays",
                            color = NeonGold
                        )
                        ProfileStatBadge(
                            value = "LVL $highestLevelAcrossGames",
                            label = "Max Level",
                            color = NeonCyan
                        )
                        ProfileStatBadge(
                            value = formatCountCompact(totalHighScoreSum),
                            label = "Total Score",
                            color = NeonGreen
                        )
                        ProfileStatBadge(
                            value = favoriteGames.size.toString(),
                            label = "Favorites",
                            color = NeonPink
                        )
                    }
                }
            }
        }

        // Saved Levels & Scores Section
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = NeonGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Saved Game Levels & Scores (${savedProgressEntries.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        if (savedProgressEntries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your reached levels, high scores, and game checkpoints are saved automatically to Cloud Firestore when you play.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(savedProgressEntries, key = { "saved_prog_${it.first.id}" }) { (game, prog) ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BloxCardBorder, RoundedCornerShape(16.dp))
                        .clickable { onPlayGame(game) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Unlocked Level ${prog.maxLevel} • High Score: ${prog.highScore}",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Button(
                            onClick = { onPlayGame(game) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color(0xFF042F2E)
                            )
                        ) {
                            Text("Resume", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Favorite Games Section
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = NeonPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Favorite Games (${favoriteGames.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        if (favoriteGames.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tap the heart icon on any game card to save your favorite worlds here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(favoriteGames, key = { "prof_fav_${it.id}" }) { game ->
                        BloxGameCard(
                            game = game,
                            isFavorite = true,
                            savedProgress = playerProgressMap[game.id],
                            onCardClick = { onGameClick(game) },
                            onPlayClick = { onPlayGame(game) },
                            onFavoriteToggle = { onToggleFavorite(game) },
                            modifier = Modifier.width(230.dp)
                        )
                    }
                }
            }
        }

        // Recently Played Section
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recently Played (${recentGames.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        if (recentGames.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BloxSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Games you launch will appear here for instant replay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recentGames, key = { "prof_rec_${it.id}" }) { game ->
                        BloxGameCard(
                            game = game,
                            isFavorite = userProfile.favoriteSet.contains(game.id),
                            savedProgress = playerProgressMap[game.id],
                            onCardClick = { onGameClick(game) },
                            onPlayClick = { onPlayGame(game) },
                            onFavoriteToggle = { onToggleFavorite(game) },
                            modifier = Modifier.width(230.dp)
                        )
                    }
                }
            }
        }

        // Subtle Version Footer (5 taps triggers hidden Owner password dialog)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable {
                        secretTapCount++
                        if (secretTapCount >= 5) {
                            secretTapCount = 0
                            onSecretOwnerTrigger()
                        }
                    }
                    .testTag("secret_version_footer"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BloxVerse Platform v1.0 • Cloud Synced",
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showEditDialog) {
        var nameInput by remember { mutableStateOf(userProfile.username) }
        var bioInput by remember { mutableStateOf(userProfile.bio) }
        var selectedAvatar by remember { mutableStateOf(userProfile.avatarKey) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = BloxSurface,
            title = {
                Text("Customize Player Profile", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Player Bio / Status", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Avatar Theme", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        avatarColors.forEach { (key, pair) ->
                            FilterChip(
                                selected = selectedAvatar == key,
                                onClick = { selectedAvatar = key },
                                label = { Text(pair.second) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = pair.first,
                                    selectedLabelColor = Color.White,
                                    containerColor = BloxSurfaceVariant,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(nameInput, selectedAvatar, bioInput)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ProfileStatBadge(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(text = label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
