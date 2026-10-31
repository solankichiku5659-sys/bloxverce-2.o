package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BuiltInHtml5Games
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.data.model.UserProfile
import com.example.ui.components.BloxGameCard
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
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SortOption
import com.google.firebase.Timestamp
import java.util.Locale

@Composable
fun HomeScreen(
    allGames: List<Game>,
    userProfile: UserProfile,
    playerProgressMap: Map<String, GameProgress> = emptyMap(),
    searchQuery: String,
    selectedCategory: String,
    selectedSort: SortOption,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onGameClick: (Game) -> Unit,
    onPlayGame: (Game) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onRefreshGames: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Normal users only see active games, ordered deterministically by fixed `sortOrder`
    val activeGames = allGames
        .filter { it.isActive }
        .sortedWith(
            compareBy<Game> { if (it.sortOrder > 0L) it.sortOrder else Long.MAX_VALUE }
                .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )

    val favoriteIds = userProfile.favoriteSet
    val recentIds = userProfile.recentList

    val filteredGames = activeGames
        .filter { game ->
            val matchesCategory = selectedCategory == "All" || game.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                game.title.contains(searchQuery, ignoreCase = true) ||
                game.description.contains(searchQuery, ignoreCase = true) ||
                game.category.contains(searchQuery, ignoreCase = true) ||
                game.creator.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
        .let { list ->
            when (selectedSort) {
                SortOption.POSITION -> list // Preserves exact fixed upload/owner position
                SortOption.POPULAR -> list.sortedWith(compareByDescending<Game> { it.plays }.thenBy { it.sortOrder })
                SortOption.TOP_RATED -> list.sortedWith(compareByDescending<Game> { it.rating }.thenBy { it.sortOrder })
                SortOption.NEWEST -> list.sortedWith(
                    compareByDescending<Game> { it.createdAt ?: Timestamp(0, 0) }.thenBy { it.sortOrder }
                )
            }
        }

    // Featured & Popular rows also respect fixed position order
    val featuredGames = activeGames.filter { it.featured }.ifEmpty { activeGames.take(3) }
    val popularGames = activeGames.sortedWith(compareByDescending<Game> { it.plays }.thenBy { it.sortOrder }).take(6)
    val recentGames = recentIds.mapNotNull { id -> activeGames.find { it.id == id } }.take(6)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 560.dp -> 2
            else -> 2
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Search & Filter Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = "Search games, obbies, tycoons, creators...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Games",
                                tint = NeonCyan
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Search",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = BloxCardBorder,
                            focusedContainerColor = BloxSurface,
                            unfocusedContainerColor = BloxSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(BuiltInHtml5Games.CATEGORIES) { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { onCategorySelect(cat) },
                                label = {
                                    Text(
                                        text = cat,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        letterSpacing = if (selected) 0.5.sp else 0.2.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple,
                                    selectedLabelColor = Color.White,
                                    containerColor = BloxSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = BloxCardBorder,
                                    selectedBorderColor = NeonCyan
                                ),
                                modifier = Modifier.testTag("category_chip_$cat")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sort Options Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SortOption.entries) { sort ->
                            val selected = selectedSort == sort
                            FilterChip(
                                selected = selected,
                                onClick = { onSortSelect(sort) },
                                label = {
                                    Text(
                                        text = sort.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        letterSpacing = 0.3.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = BloxSurfaceVariant.copy(alpha = 0.5f),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = NeonCyan.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.testTag("sort_chip_${sort.name}")
                            )
                        }
                    }
                }
            }

            // If no games have been uploaded by the Owner yet, display a clean empty state
            if (activeGames.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = BloxSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.horizontalGradient(listOf(NeonPurple, NeonCyan)),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Games Uploaded Yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Only games uploaded by the BloxVerse Owner appear here. Once a game is published, it remains saved in Cloud Firestore across reinstalls and syncs to all devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            OutlinedButton(
                                onClick = onRefreshGames,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("home_refresh_empty_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Catalog",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Refresh Live Catalog", color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // 2. Featured Hero Carousel (shown when not filtering by search text)
                if (searchQuery.isBlank() && selectedCategory == "All" && featuredGames.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(
                                title = "Featured Experiences",
                                subtitle = "Official worlds published by the BloxVerse Owner",
                                icon = Icons.Default.Bolt,
                                iconTint = NeonGold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(featuredGames, key = { "feat_${it.id}" }) { game ->
                                    FeaturedHeroCard(
                                        game = game,
                                        savedProgress = playerProgressMap[game.id],
                                        onCardClick = { onGameClick(game) },
                                        onPlayClick = { onPlayGame(game) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recently Played Row (if player has history)
                if (searchQuery.isBlank() && selectedCategory == "All" && recentGames.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(
                                title = "Continue Playing",
                                subtitle = "Resume from your saved level & high score checkpoints",
                                icon = Icons.Default.History,
                                iconTint = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(recentGames, key = { "rec_${it.id}" }) { game ->
                                    BloxGameCard(
                                        game = game,
                                        isFavorite = favoriteIds.contains(game.id),
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
                }

                // 4. Trending & Popular Row
                if (searchQuery.isBlank() && selectedCategory == "All" && popularGames.size > 1) {
                    item {
                        Column {
                            SectionHeader(
                                title = "Popular Games",
                                subtitle = "Most played games across the BloxVerse",
                                icon = Icons.Default.Whatshot,
                                iconTint = NeonPink
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(popularGames, key = { "pop_${it.id}" }) { game ->
                                    BloxGameCard(
                                        game = game,
                                        isFavorite = favoriteIds.contains(game.id),
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
                }



                // 5. All / Filtered Games Grid in Exact Fixed Position
                item {
                    SectionHeader(
                        title = if (selectedCategory == "All" && searchQuery.isBlank()) {
                            "All Published Games (${filteredGames.size})"
                        } else {
                            "Matching Games (${filteredGames.size})"
                        },
                        subtitle = "Locked in fixed position • Levels & scores auto-saved to Cloud",
                        icon = Icons.Default.NewReleases,
                        iconTint = NeonPurple
                    )
                }

                if (filteredGames.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = BloxSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No games match your filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Try clearing your search query or selecting 'All' categories.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    val rows = filteredGames.chunked(columns)
                    items(rows) { rowGames ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (game in rowGames) {
                                BloxGameCard(
                                    game = game,
                                    isFavorite = favoriteIds.contains(game.id),
                                    savedProgress = playerProgressMap[game.id],
                                    onCardClick = { onGameClick(game) },
                                    onPlayClick = { onPlayGame(game) },
                                    onFavoriteToggle = { onToggleFavorite(game) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowGames.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(iconTint.copy(alpha = 0.25f), iconTint.copy(alpha = 0.08f))
                    )
                )
                .border(1.2.dp, iconTint.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp
            )
        }
    }
}

@Composable
private fun FeaturedHeroCard(
    game: Game,
    savedProgress: GameProgress?,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BloxSurface),
        modifier = Modifier
            .width(320.dp)
            .height(210.dp)
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(NeonPurple, NeonCyan)),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onCardClick)
            .testTag("featured_card_${game.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GameThumbnailBanner(
                thumbnailKey = game.thumbnail,
                category = game.category,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color(0xFF080B14).copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NeonPurple)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FEATURED",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = game.category.uppercase(),
                        color = NeonCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                if (savedProgress != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, NeonGold, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LVL ${savedProgress.maxLevel}",
                            color = NeonGold,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = NeonGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", game.rating)} • ${formatCountCompact(game.plays)} plays",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color(0xFF042F2E)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (savedProgress != null && savedProgress.maxLevel > 1) "RESUME" else "PLAY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
