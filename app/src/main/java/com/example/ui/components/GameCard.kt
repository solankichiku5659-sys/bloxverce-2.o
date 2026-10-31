package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Game
import com.example.data.model.GameProgress
import com.example.ui.theme.BloxCardBorder
import com.example.ui.theme.BloxSurface
import com.example.ui.theme.BloxSurfaceVariant
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondary
import java.util.Locale

fun formatCountCompact(count: Long): String {
    return when {
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

/**
 * Renders the game's thumbnail artwork:
 * - Supports custom Owner-uploaded images (`data:image/...;base64,...`) synced via Firestore
 * - Supports high-res generated drawables (`obby_neon`, `cyber_tycoon`, `hyper_sim`)
 * - Supports category-themed neon procedural canvas artwork
 */
@Composable
fun GameThumbnailBanner(
    thumbnailKey: String,
    category: String,
    modifier: Modifier = Modifier
) {
    val customBitmap = remember(thumbnailKey) {
        if (thumbnailKey.startsWith("data:image", ignoreCase = true) && thumbnailKey.contains("base64,")) {
            try {
                val b64 = thumbnailKey.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(modifier = modifier.background(BloxSurfaceVariant)) {
        when {
            customBitmap != null -> {
                Image(
                    bitmap = customBitmap,
                    contentDescription = "Custom Game Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            thumbnailKey == "obby_neon" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_obby_thumb),
                    contentDescription = "Neon Obby Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            thumbnailKey == "cyber_tycoon" || thumbnailKey == "hyper_sim" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner),
                    contentDescription = "Game Universe Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val (c1, c2, c3) = when (category) {
                    "Action" -> Triple(Color(0xFF31104F), NeonPink, NeonCyan)
                    "Adventure" -> Triple(Color(0xFF06342E), NeonGreen, NeonBlue)
                    "Puzzle" -> Triple(Color(0xFF1E1B4B), NeonGold, NeonPurple)
                    "Tycoon" -> Triple(Color(0xFF0F2942), NeonCyan, NeonGreen)
                    else -> Triple(Color(0xFF1A103C), NeonPurple, NeonCyan)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(c1, c2.copy(alpha = 0.75f), c3.copy(alpha = 0.85f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                    val step = size.width / 6f
                    for (i in 1..5) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(i * step, 0f),
                            end = Offset(i * step, size.height),
                            strokeWidth = 2f
                        )
                    }
                    drawCircle(
                        color = c3.copy(alpha = 0.35f),
                        radius = size.minDimension * 0.32f,
                        center = Offset(size.width * 0.72f, size.height * 0.42f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = size.minDimension * 0.16f,
                        center = Offset(size.width * 0.3f, size.height * 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Roblox-inspired Game Card displaying:
 * - Game Thumbnail
 * - Game Title
 * - Creator Name
 * - Saved Player Level & High Score badge (when played)
 * - Total Plays, Likes & Rating
 */
@Composable
fun BloxGameCard(
    game: Game,
    isFavorite: Boolean,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    savedProgress: GameProgress? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BloxSurface),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(BloxCardBorder.copy(alpha = 0.95f), BloxCardBorder.copy(alpha = 0.35f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onCardClick)
            .testTag("game_card_${game.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
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
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Category & APK Pill (Top Start)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = game.category.uppercase(),
                            color = NeonCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }

                    if (game.isApkGame) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(NeonGreen.copy(alpha = 0.25f))
                                .border(1.dp, NeonGreen.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "APK",
                                    color = NeonGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }
                }

                // Favorite Button (Top End) - minimum 48dp touch target
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .testTag("fav_button_${game.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        tint = if (isFavorite) NeonPink else Color.White
                    )
                }

                // Rating Badge (Bottom Start)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, NeonGold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = NeonGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", game.rating),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Quick Play / Launch FAB button (Bottom End)
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (game.isApkGame) listOf(NeonCyan, NeonGreen)
                                else listOf(NeonPurple, NeonCyan)
                            )
                        )
                        .border(1.2.dp, if (game.isApkGame) NeonGreen.copy(alpha = 0.8f) else NeonCyan.copy(alpha = 0.8f), CircleShape)
                        .testTag("quick_play_${game.id}")
                ) {
                    Icon(
                        imageVector = if (game.isApkGame) Icons.Default.Android else Icons.Default.PlayArrow,
                        contentDescription = if (game.isApkGame) "Launch ${game.title}" else "Play ${game.title}",
                        tint = if (game.isApkGame) Color(0xFF042F2E) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "by ",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = game.creator,
                        color = NeonCyan.copy(alpha = 0.9f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (savedProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonPurple.copy(alpha = 0.18f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = NeonGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LVL ${savedProgress.maxLevel} • Best ${formatCountCompact(savedProgress.highScore)}",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Total Plays",
                            tint = NeonGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${formatCountCompact(game.plays)} plays",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Total Likes",
                            tint = NeonPink,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatCountCompact(game.likes),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
