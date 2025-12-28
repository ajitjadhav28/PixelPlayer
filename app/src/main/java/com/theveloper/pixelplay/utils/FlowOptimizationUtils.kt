package com.theveloper.pixelplay.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

/**
 * Utility object for optimizing Flow operations.
 * Provides helper functions for flow caching and sharing strategies.
 */
object FlowOptimizationUtils {

    /**
     * Standard sharing started configuration for most flows.
     * Stops collecting 5 seconds after the last subscriber leaves.
     * This balances memory usage with responsiveness.
     */
    val WhileSubscribed5s = SharingStarted.WhileSubscribed(5000L)

    /**
     * Eager sharing - starts immediately and never stops.
     * Use for critical flows that should always be hot (e.g., player state).
     */
    val Eagerly = SharingStarted.Eagerly

    /**
     * Lazy sharing - starts when first subscriber appears and never stops.
     * Use for flows that are expensive to recreate but not always needed.
     */
    val Lazily = SharingStarted.Lazily

    /**
     * Convert a cold Flow to a hot StateFlow with optimized sharing.
     * Automatically stops collecting after timeout when no subscribers.
     *
     * @param scope The coroutine scope for the StateFlow
     * @param initialValue Initial value before first emission
     * @param stopTimeoutMs Milliseconds to wait before stopping collection (default: 5000)
     * @return Hot StateFlow that caches the latest value
     */
    fun <T> Flow<T>.toHotStateFlow(
        scope: CoroutineScope,
        initialValue: T,
        stopTimeoutMs: Long = 5000L
    ): StateFlow<T> {
        return stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMs),
            initialValue = initialValue
        )
    }

    /**
     * Convert a Flow to an eagerly-started StateFlow.
     * Use for flows that represent critical application state.
     *
     * @param scope The coroutine scope for the StateFlow
     * @param initialValue Initial value before first emission
     * @return Hot StateFlow that starts immediately
     */
    fun <T> Flow<T>.toEagerStateFlow(
        scope: CoroutineScope,
        initialValue: T
    ): StateFlow<T> {
        return stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = initialValue
        )
    }

    /**
     * Convert a Flow to a lazily-started StateFlow.
     * Starts when first subscriber appears and keeps running.
     *
     * @param scope The coroutine scope for the StateFlow
     * @param initialValue Initial value before first emission
     * @return Hot StateFlow that starts lazily
     */
    fun <T> Flow<T>.toLazyStateFlow(
        scope: CoroutineScope,
        initialValue: T
    ): StateFlow<T> {
        return stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = initialValue
        )
    }
}

/**
 * Cache configuration for different types of data.
 */
object CacheConfig {

    /**
     * Cache time for frequently accessed, fast-changing data.
     * Examples: player position, UI state
     */
    const val SHORT_CACHE_MS = 1000L // 1 second

    /**
     * Cache time for moderately accessed, slow-changing data.
     * Examples: song lists, album lists
     */
    const val MEDIUM_CACHE_MS = 5000L // 5 seconds (default)

    /**
     * Cache time for infrequently accessed, rarely changing data.
     * Examples: settings, preferences
     */
    const val LONG_CACHE_MS = 30000L // 30 seconds

    /**
     * Cache time for static data that rarely changes.
     * Examples: total song count, library statistics
     */
    const val VERY_LONG_CACHE_MS = 300000L // 5 minutes
}

