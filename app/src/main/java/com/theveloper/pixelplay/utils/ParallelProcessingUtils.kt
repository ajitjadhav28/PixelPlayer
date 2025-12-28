package com.theveloper.pixelplay.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Utility object for parallel processing operations.
 * Provides helper functions to process collections in parallel with optimal batch sizes.
 */
object ParallelProcessingUtils {

    /**
     * Optimal batch size for CPU-bound operations (e.g., metadata extraction).
     * Based on typical Android device core count (4-8 cores).
     */
    private const val CPU_BATCH_SIZE = 50

    /**
     * Optimal batch size for I/O-bound operations (e.g., file reading).
     * Can be higher since I/O operations spend time waiting.
     */
    private const val IO_BATCH_SIZE = 100

    /**
     * Process a collection in parallel batches using the IO dispatcher.
     * Best for I/O-bound operations like file reading, network requests.
     *
     * @param collection The collection to process
     * @param batchSize Number of items to process in parallel (default: IO_BATCH_SIZE)
     * @param transform The transformation to apply to each item
     * @return List of transformed results in original order
     */
    suspend fun <T, R> processInParallelIO(
        collection: Collection<T>,
        batchSize: Int = IO_BATCH_SIZE,
        transform: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.IO) {
        if (collection.isEmpty()) return@withContext emptyList()

        coroutineScope {
            collection.chunked(batchSize).flatMap { batch ->
                batch.map { item ->
                    async { transform(item) }
                }.awaitAll()
            }
        }
    }

    /**
     * Process a collection in parallel batches using the Default dispatcher.
     * Best for CPU-bound operations like data processing, parsing.
     *
     * @param collection The collection to process
     * @param batchSize Number of items to process in parallel (default: CPU_BATCH_SIZE)
     * @param transform The transformation to apply to each item
     * @return List of transformed results in original order
     */
    suspend fun <T, R> processInParallelCPU(
        collection: Collection<T>,
        batchSize: Int = CPU_BATCH_SIZE,
        transform: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.Default) {
        if (collection.isEmpty()) return@withContext emptyList()

        coroutineScope {
            collection.chunked(batchSize).flatMap { batch ->
                batch.map { item ->
                    async { transform(item) }
                }.awaitAll()
            }
        }
    }

    /**
     * Process a collection in parallel with progress callback.
     * Useful for showing progress during long-running operations.
     *
     * @param collection The collection to process
     * @param batchSize Number of items to process in parallel
     * @param onProgress Callback invoked after each batch (processed count, total count)
     * @param transform The transformation to apply to each item
     * @return List of transformed results in original order
     */
    suspend fun <T, R> processWithProgress(
        collection: Collection<T>,
        batchSize: Int = IO_BATCH_SIZE,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
        transform: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.IO) {
        if (collection.isEmpty()) return@withContext emptyList()

        val total = collection.size
        var processed = 0
        val results = mutableListOf<R>()

        coroutineScope {
            collection.chunked(batchSize).forEach { batch ->
                val batchResults = batch.map { item ->
                    async { transform(item) }
                }.awaitAll()

                results.addAll(batchResults)
                processed += batch.size
                onProgress(processed, total)
            }
        }

        results
    }

    /**
     * Process a collection in parallel and filter out null results.
     * Useful when some transformations may fail.
     *
     * @param collection The collection to process
     * @param batchSize Number of items to process in parallel
     * @param transform The transformation to apply (returns null on failure)
     * @return List of non-null transformed results
     */
    suspend fun <T, R> processAndFilterNulls(
        collection: Collection<T>,
        batchSize: Int = IO_BATCH_SIZE,
        transform: suspend (T) -> R?
    ): List<R> = withContext(Dispatchers.IO) {
        if (collection.isEmpty()) return@withContext emptyList()

        coroutineScope {
            collection.chunked(batchSize).flatMap { batch ->
                batch.map { item ->
                    async { transform(item) }
                }.awaitAll().filterNotNull()
            }
        }
    }

    /**
     * Get the optimal batch size based on the operation type and collection size.
     *
     * @param collectionSize Size of the collection to process
     * @param isIOBound Whether the operation is I/O-bound (true) or CPU-bound (false)
     * @return Optimal batch size
     */
    fun getOptimalBatchSize(collectionSize: Int, isIOBound: Boolean = true): Int {
        val baseBatchSize = if (isIOBound) IO_BATCH_SIZE else CPU_BATCH_SIZE

        return when {
            collectionSize < 10 -> collectionSize // Don't bother with batching for small collections
            collectionSize < baseBatchSize -> collectionSize / 2 // Half for medium collections
            else -> baseBatchSize // Use full batch size for large collections
        }
    }
}

