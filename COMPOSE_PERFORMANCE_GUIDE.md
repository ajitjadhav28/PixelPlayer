# Compose UI Performance Optimization Guide

## Overview
This guide provides best practices for optimizing Jetpack Compose UI performance in PixelPlayer.

## 1. Minimize Recompositions

### 1.1 Use derivedStateOf for Computed Values
```kotlin
// BAD - Recomposes on every songs list change even if filtered result is same
val filteredSongs = songs.filter { it.isFavorite }

// GOOD - Only recomposes when the filtered result actually changes
val filteredSongs by remember {
    derivedStateOf {
        songs.filter { it.isFavorite }
    }
}
```

### 1.2 Use Stable Data Classes
```kotlin
// BAD - List causes recomposition on reference change
data class UiState(val songs: List<Song>)

// GOOD - ImmutableList is stable
import kotlinx.collections.immutable.ImmutableList
data class UiState(val songs: ImmutableList<Song>)
```

### 1.3 Use Keys in LazyColumn
```kotlin
// BAD - Items recreated on list changes
LazyColumn {
    items(songs) { song ->
        SongItem(song)
    }
}

// GOOD - Items reused when possible
LazyColumn {
    items(
        items = songs,
        key = { song -> song.id }
    ) { song ->
        SongItem(song)
    }
}
```

## 2. Optimize LazyLists

### 2.1 Use contentType for Better Recycling
```kotlin
LazyColumn {
    items(
        items = items,
        key = { it.id },
        contentType = { it.type } // Groups similar items for better recycling
    ) { item ->
        when (item) {
            is Song -> SongItem(item)
            is Album -> AlbumItem(item)
        }
    }
}
```

### 2.2 Prefetch Items
```kotlin
// Already implemented in PrefetchAlbumNeighbors.kt
// Prefetches items before they're visible
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize()
) {
    // Items automatically prefetched based on scroll direction
    items(songs, key = { it.id }) { song ->
        SongItem(song)
    }
}
```

### 2.3 Avoid Complex Calculations in Item Lambdas
```kotlin
// BAD - Calculation happens for every item
LazyColumn {
    items(songs) { song ->
        val duration = formatDuration(song.duration) // Calculated each recomposition
        SongItem(song, duration)
    }
}

// GOOD - Pre-calculate or memoize
val formattedSongs = remember(songs) {
    songs.map { it to formatDuration(it.duration) }
}
LazyColumn {
    items(formattedSongs) { (song, duration) ->
        SongItem(song, duration)
    }
}
```

## 3. Image Loading Optimization

### 3.1 Use Appropriate Image Sizes
```kotlin
// BAD - Loads full resolution for thumbnail
AsyncImage(
    model = albumArtUri,
    contentDescription = null,
    modifier = Modifier.size(48.dp)
)

// GOOD - Requests appropriately sized image
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(albumArtUri)
        .size(48.dp.toPx()) // Coil loads appropriate size
        .crossfade(true)
        .build(),
    contentDescription = null,
    modifier = Modifier.size(48.dp)
)
```

### 3.2 Cache Images Properly
```kotlin
// Already implemented in AlbumArtUtils.kt with LRU cache
// Images are cached both in memory and on disk
```

### 3.3 Prefetch Images for Smooth Scrolling
```kotlin
// Prefetch album art for visible and upcoming items
LaunchedEffect(visibleItems) {
    visibleItems.forEach { song ->
        // Prefetch happens in background
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(song.albumArtUriString)
                .build()
        )
    }
}
```

## 4. State Management

### 4.1 Hoist State Appropriately
```kotlin
// BAD - State too low, causes unnecessary recompositions
@Composable
fun SongList(songs: List<Song>) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = songs.filter { it.title.contains(searchQuery) }
    
    SearchBar(searchQuery, onQueryChange = { searchQuery = it })
    LazyColumn { items(filtered) { ... } }
}

// GOOD - State in ViewModel, only UI recomposes
@Composable
fun SongList(
    searchQuery: String,
    filteredSongs: List<Song>,
    onQueryChange: (String) -> Unit
) {
    SearchBar(searchQuery, onQueryChange)
    LazyColumn { items(filteredSongs) { ... } }
}
```

### 4.2 Use collectAsStateWithLifecycle
```kotlin
// BAD - Collects even when app in background
val songs by viewModel.songs.collectAsState()

// GOOD - Stops collecting when app in background
val songs by viewModel.songs.collectAsStateWithLifecycle()
```

## 5. Animation Performance

### 5.1 Use Hardware Acceleration
```kotlin
// Enable hardware layer during animation for better performance
Box(
    modifier = Modifier
        .graphicsLayer {
            alpha = animatedAlpha
            scaleX = animatedScale
            scaleY = animatedScale
        }
)
```

### 5.2 Avoid Animating Layout
```kotlin
// BAD - Animates layout, expensive
AnimatedVisibility(visible) {
    Box(modifier = Modifier.fillMaxWidth())
}

// GOOD - Animates only visual properties
Box(
    modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = if (visible) 1f else 0f }
)
```

## 6. Modifier Chain Optimization

### 6.1 Order Matters
```kotlin
// BAD - Clickable area is small padding area
Modifier
    .padding(16.dp)
    .clickable { }

// GOOD - Clickable area includes padding
Modifier
    .clickable { }
    .padding(16.dp)
```

### 6.2 Reuse Modifiers
```kotlin
// Create reusable modifier chains
val commonItemModifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 8.dp)
    .clip(RoundedCornerShape(8.dp))

// Use in multiple places
SongItem(modifier = commonItemModifier)
AlbumItem(modifier = commonItemModifier)
```

## 7. Remember and Caching

### 7.1 Remember Expensive Calculations
```kotlin
// BAD - Recalculates every recomposition
val sortedSongs = songs.sortedBy { it.title }

// GOOD - Only recalculates when songs change
val sortedSongs = remember(songs) {
    songs.sortedBy { it.title }
}
```

### 7.2 Use rememberUpdatedState for Callbacks
```kotlin
@Composable
fun SongItem(
    song: Song,
    onPlay: (Song) -> Unit // May change every recomposition
) {
    // Capture current callback
    val currentOnPlay by rememberUpdatedState(onPlay)
    
    // This composable won't recompose when onPlay changes
    Button(onClick = { currentOnPlay(song) }) {
        Text("Play")
    }
}
```

## 8. Composition Local

### 8.1 Avoid Overusing CompositionLocal
```kotlin
// BAD - Creates new CompositionLocal for frequently changing data
val LocalPlayerState = compositionLocalOf { PlayerState() }

// GOOD - Pass as parameter or use ViewModel
@Composable
fun MusicScreen(playerState: PlayerState)
```

## 9. Performance Monitoring

### 9.1 Enable Compose Compiler Metrics
Already enabled in `compose_stability.conf` - check metrics in build output.

### 9.2 Use Compose Performance Profiling
```kotlin
// Add to debug builds
if (BuildConfig.DEBUG) {
    // Profile recompositions
    Composition(...)
}
```

### 9.3 Check Skippability
View compose compiler reports in:
- `app/build/compose_compiler_reports/`
- Look for `unstable`, `runtime stability` issues

## 10. Specific PixelPlayer Optimizations

### 10.1 Song List
```kotlin
// Use contentType for better recycling
LazyColumn {
    items(
        items = songs,
        key = { it.id },
        contentType = { "song" }
    ) { song ->
        SongItem(
            song = song,
            // Pass primitive types when possible
            onPlay = onPlaySong
        )
    }
}
```

### 10.2 Album Grid
```kotlin
// Use GridCells.Adaptive for responsive grid
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 150.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(
        items = albums,
        key = { it.id },
        contentType = { "album" }
    ) { album ->
        AlbumCard(album)
    }
}
```

### 10.3 Player Sheet
```kotlin
// Use graphicsLayer for smooth animations
Box(
    modifier = Modifier
        .graphicsLayer {
            translationY = sheetOffset
            alpha = sheetAlpha
        }
        .fillMaxSize()
)
```

## Performance Checklist

- [ ] LazyList items have keys
- [ ] LazyList items use contentType
- [ ] Expensive calculations use `remember` or `derivedStateOf`
- [ ] State is hoisted appropriately
- [ ] Using `collectAsStateWithLifecycle` for flows
- [ ] Images are properly sized and cached
- [ ] Animations use `graphicsLayer` when possible
- [ ] Modifier chains are ordered correctly
- [ ] Using stable/immutable data classes
- [ ] Avoiding unnecessary recompositions

## Expected Performance Impact

Following these guidelines should result in:
- **60 FPS** scroll performance even with large lists
- **< 16ms** frame time for most UI operations
- **30-50% fewer** recompositions
- **Smoother** animations and transitions
- **Better** battery life due to reduced CPU usage

## Tools for Monitoring

1. **Layout Inspector** - View composition tree
2. **Android Profiler** - Monitor CPU, memory, frame time
3. **Compose Compiler Reports** - Check stability and skippability
4. **Systrace** - Deep performance analysis

## References

- [Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Stability in Compose](https://developer.android.com/jetpack/compose/performance/stability)
- [LazyList Performance](https://developer.android.com/jetpack/compose/lists#performance)

