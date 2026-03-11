package com.cloud.movietab

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloud.TMDBConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import androidx.core.content.edit

data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val releaseDate: String,
    val voteAverage: Double,
    val genreIds: List<Int>
)

@Composable
fun MovieDiscoveryTabContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf<Int?>(null) } // null = Alle Genres
    var savedMovieIds by remember { mutableStateOf(loadSavedMovies(context)) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var showSavedOnly by remember { mutableStateOf(false) }

    val genres = mapOf(
        null to "🎬 Alle",
        28 to "Action",
        35 to "Komödie",
        18 to "Drama",
        27 to "Horror",
        878 to "Sci-Fi",
        53 to "Thriller",
        10749 to "Romantik",
        16 to "Animation"
    )

    fun loadMovies() {
        scope.launch {
            isLoading = true
            try {
                val result = fetchMoviesFromTMDB(selectedGenre)
                movies = result.shuffled().take(20)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadMovies()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        // Genre Auswahl
        Text(
            "Genre wählen",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(genres.entries.toList()) { entry ->
                FilterChip(
                    selected = selectedGenre == entry.key,
                    onClick = {
                        selectedGenre = entry.key
                        loadMovies()
                    },
                    label = { Text(entry.value) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4CAF50),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF333333),
                        labelColor = Color.Gray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { loadMovies() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("🎲 Neue Filme")
            }

            Button(
                onClick = { showSavedOnly = !showSavedOnly },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showSavedOnly) Color(0xFFFF9800) else Color(0xFF2196F3)
                )
            ) {
                Text(if (showSavedOnly) "🎬 Alle anzeigen" else "⭐ Gemerkte (${savedMovieIds.size})")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Film Liste
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        } else {
            val displayMovies = if (showSavedOnly) {
                movies.filter { savedMovieIds.contains(it.id) }
            } else {
                movies
            }

            if (displayMovies.isEmpty() && showSavedOnly) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Keine gemerkten Filme vorhanden",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayMovies) { movie ->
                        MovieCard(
                            movie = movie,
                            isSaved = savedMovieIds.contains(movie.id),
                            onSaveToggle = {
                                savedMovieIds = if (savedMovieIds.contains(movie.id)) {
                                    savedMovieIds - movie.id
                                } else {
                                    savedMovieIds + movie.id
                                }
                                saveMoviesToPrefs(context, savedMovieIds)
                            },
                            onClick = {
                                selectedMovie = movie
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedMovie?.let { movie ->
        MovieDetailDialog(
            movie = movie,
            isSaved = savedMovieIds.contains(movie.id),
            onDismiss = { selectedMovie = null },
            onSaveToggle = {
                savedMovieIds = if (savedMovieIds.contains(movie.id)) {
                    savedMovieIds - movie.id
                } else {
                    savedMovieIds + movie.id
                }
                saveMoviesToPrefs(context, savedMovieIds)
            }
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun MovieCard(
    movie: Movie,
    isSaved: Boolean,
    onSaveToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Poster
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://image.tmdb.org/t/p/w200${movie.posterPath}")
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                contentScale = ContentScale.Crop
            )

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onSaveToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = if (isSaved) "⭐" else "☆",
                            fontSize = 24.sp
                        )
                    }
                }

                Text(
                    text = "⭐ ${String.format("%.1f", movie.voteAverage)} | ${movie.releaseDate.take(4)}",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = movie.overview.ifEmpty { "Keine Beschreibung verfügbar" },
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

suspend fun fetchMoviesFromTMDB(genreId: Int?): List<Movie> = withContext(Dispatchers.IO) {
    val apiKey = TMDBConfig.APIKEY
    val randomPage = (1..5).random()
    val genreParam = if (genreId != null) "&with_genres=$genreId" else ""
    val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey${genreParam}&page=$randomPage&language=de-DE&sort_by=popularity.desc"

    val response = URL(url).readText()
    val json = JSONObject(response)
    val results = json.getJSONArray("results")

    (0 until results.length()).map { i ->
        val movieJson = results.getJSONObject(i)
        Movie(
            id = movieJson.getInt("id"),
            title = movieJson.getString("title"),
            overview = movieJson.optString("overview", ""),
            posterPath = movieJson.optString("poster_path", ""),
            releaseDate = movieJson.optString("release_date", ""),
            voteAverage = movieJson.getDouble("vote_average"),
            genreIds = (0 until movieJson.getJSONArray("genre_ids").length())
                .map { movieJson.getJSONArray("genre_ids").getInt(it) }
        )
    }
}

fun loadSavedMovies(context: Context): Set<Int> {
    val prefs = context.getSharedPreferences("cloud_app_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("saved_movies", emptySet())
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet() ?: emptySet()
}

fun saveMoviesToPrefs(context: Context, movieIds: Set<Int>) {
    val prefs = context.getSharedPreferences("cloud_app_prefs", Context.MODE_PRIVATE)
    prefs.edit(commit = true) { putStringSet("saved_movies", movieIds.map { it.toString() }.toSet()) }
}

@SuppressLint("DefaultLocale")
@Composable
fun MovieDetailDialog(
    movie: Movie,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onSaveToggle: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSaveToggle) {
                    Text(
                        text = if (isSaved) "⭐" else "☆",
                        fontSize = 28.sp
                    )
                }
            }
        },
        text = {
            Column {
                // Poster
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://image.tmdb.org/t/p/w500${movie.posterPath}")
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rating und Datum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "⭐ ${String.format("%.1f", movie.voteAverage)}/10",
                        color = Color(0xFFFFD700),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📅 ${movie.releaseDate}",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Beschreibung
                Text(
                    text = "Beschreibung:",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = movie.overview.ifEmpty { "Keine Beschreibung verfügbar" },
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Schließen")
            }
        }
    )
}