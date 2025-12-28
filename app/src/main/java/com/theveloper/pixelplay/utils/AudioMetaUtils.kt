package com.theveloper.pixelplay.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.theveloper.pixelplay.data.database.MusicDao
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class AudioMeta(
    val mimeType: String?,
    val bitrate: Int?,      // bits per second
    val sampleRate: Int?   // Hz
)

object AudioMetaUtils {

    // In-memory cache to avoid re-reading metadata for recently processed files
    // Using thread-safe LRU cache with LinkedHashMap
    private val metadataCache = object : LinkedHashMap<Long, AudioMeta>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, AudioMeta>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val cacheLock = Any()
    private const val MAX_CACHE_SIZE = 1000 // Increased cache size

    /**
     * Returns audio metadata for a given file path.
     * Tries MediaMetadataRetriever first, then falls back to MediaExtractor.
     */
    suspend fun getAudioMetadata(musicDao: MusicDao, id: Long, filePath: String, deepScan: Boolean): AudioMeta {
        // Check in-memory cache first
        synchronized(cacheLock) {
            metadataCache[id]?.let { return it }
        }

        val cached = musicDao.getAudioMetadataById(id)
        if (!deepScan && cached != null &&
            cached.mimeType != null &&
            cached.bitrate != null &&
            cached.sampleRate != null
        ) {
            synchronized(cacheLock) {
                metadataCache[id] = cached
            }
            return cached
        }

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return AudioMeta(null, null, null)
        }

        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null

        // Try MediaMetadataRetriever (faster and more reliable for most formats)
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
        } catch (e: Exception) {
            Log.w("AudioMetaUtils", "Retriever failed for $filePath: ${e.message}")
        } finally {
            retriever?.release()
        }

        // Only try MediaExtractor if we're missing critical data
        if ((mimeType == null || sampleRate == null) && deepScan) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(filePath)
                for (i in 0 until extractor.trackCount) {
                    val format: MediaFormat = extractor.getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME)
                    if (trackMime?.startsWith("audio/") == true) {
                        mimeType = mimeType ?: trackMime
                        sampleRate = sampleRate ?: format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        bitrate = bitrate ?: if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else null
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioMetaUtils", "Extractor failed for $filePath: ${e.message}")
            } finally {
                extractor?.release()
            }
        }

        val result = AudioMeta(mimeType, bitrate, sampleRate)
        
        // Cache the result with LRU eviction handled automatically
        synchronized(cacheLock) {
            metadataCache[id] = result
        }

        return result
    }

    fun mimeTypeToFormat(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "audio/mpeg" -> "mp3"
            "audio/flac" -> "flac"
            "audio/x-wav", "audio/wav" -> "wav"
            "audio/ogg" -> "ogg"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/amr" -> "amr"
            else -> "-"
        }
    }
}
