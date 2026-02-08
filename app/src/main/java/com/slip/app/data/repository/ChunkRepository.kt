package com.slip.app.data.repository

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.ChunkMetadata
import com.slip.app.domain.model.FileChunk
import com.slip.app.domain.model.ChunkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing chunk persistence and tracking
 */
class ChunkRepository private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ChunkRepository"
        private const val CHUNKS_DIR = "chunks"
        private const val METADATA_FILE = "chunk_metadata.json"
        
        @Volatile
        private var INSTANCE: ChunkRepository? = null
        
        fun getInstance(context: Context): ChunkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChunkRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val chunksDir = File(context.filesDir, CHUNKS_DIR)
    private val metadataFile = File(chunksDir, METADATA_FILE)
    
    // In-memory cache for active chunks
    private val chunkCache = ConcurrentHashMap<String, MutableList<FileChunk>>()
    private val metadataCache = ConcurrentHashMap<String, ChunkMetadata>()
    
    init {
        chunksDir.mkdirs()
        loadPersistedData()
    }
    
    /**
     * Save chunk metadata to disk
     */
    suspend fun saveChunkMetadata(metadata: ChunkMetadata) = withContext(Dispatchers.IO) {
        try {
            metadataCache[metadata.fileId] = metadata
            
            val json = com.google.gson.Gson().toJson(metadata)
            metadataFile.writeText(json)
            
            Log.d(TAG, "Saved chunk metadata for ${metadata.fileId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chunk metadata", e)
        }
    }
    
    /**
     * Load chunk metadata from disk
     */
    private fun loadPersistedData() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val metadata = com.google.gson.Gson().fromJson(json, ChunkMetadata::class.java)
                metadata?.let {
                    metadataCache[it.fileId] = it
                    Log.d(TAG, "Loaded chunk metadata for ${it.fileId}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted chunk metadata", e)
        }
    }
    
    /**
     * Save chunk progress
     */
    suspend fun saveChunkProgress(fileId: String, chunks: List<FileChunk>) = withContext(Dispatchers.IO) {
        try {
            chunkCache[fileId] = chunks.toMutableList()
            
            // Update metadata
            val metadata = metadataCache[fileId]
            if (metadata != null) {
                val completedChunks = chunks.filter { it.isComplete() }.map { it.index }.toSet()
                val failedChunks = chunks.filter { it.hasFailed() }.map { it.index }.toSet()
                
                val updatedMetadata = metadata.copy(
                    completedChunks = completedChunks,
                    failedChunks = failedChunks,
                    totalTransferredBytes = metadata.getTransferredBytes(metadata.chunkSize)
                )
                
                saveChunkMetadata(updatedMetadata)
            }
            
            Log.d(TAG, "Saved chunk progress for $fileId: ${chunks.count { it.isComplete() }}/${chunks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chunk progress", e)
        }
    }
    
    /**
     * Get chunks for a file
     */
    fun getChunks(fileId: String): List<FileChunk> {
        return chunkCache[fileId]?.toList() ?: emptyList()
    }
    
    /**
     * Get chunk metadata for a file
     */
    fun getChunkMetadata(fileId: String): ChunkMetadata? {
        return metadataCache[fileId]
    }
    
    /**
     * Update chunk status
     */
    suspend fun updateChunkStatus(fileId: String, chunkIndex: Int, status: ChunkStatus, errorMessage: String? = null) {
        val chunks = chunkCache[fileId]?.toMutableList() ?: return
        
        val chunkIndex = chunks.indexOfFirst { it.index == chunkIndex }
        if (chunkIndex != -1) {
            val chunk = chunks[chunkIndex]
            chunks[chunkIndex] = when (status) {
                ChunkStatus.COMPLETED -> chunk.withStatus(status)
                ChunkStatus.FAILED -> chunk.withError(errorMessage ?: "Unknown error")
                else -> chunk.withStatus(status)
            }
            
            saveChunkProgress(fileId, chunks)
        }
    }
    
    /**
     * Update chunk progress
     */
    suspend fun updateChunkProgress(fileId: String, chunkIndex: Int, bytesTransferred: Long) {
        val chunks = chunkCache[fileId]?.toMutableList() ?: return
        
        val chunkIndex = chunks.indexOfFirst { it.index == chunkIndex }
        if (chunkIndex != -1) {
            val chunk = chunks[chunkIndex]
            chunks[chunkIndex] = chunk.withProgress(bytesTransferred)
            
            saveChunkProgress(fileId, chunks)
        }
    }
    
    /**
     * Get pending chunks for a file
     */
    fun getPendingChunks(fileId: String): List<FileChunk> {
        return getChunks(fileId).filter { it.status == ChunkStatus.PENDING }
    }
    
    /**
     * Get failed chunks that can be retried
     */
    fun getRetryableChunks(fileId: String, maxRetries: Int = 3): List<FileChunk> {
        return getChunks(fileId).filter { it.hasFailed() && it.canRetry(maxRetries) }
    }
    
    /**
     * Get completed chunks for a file
     */
    fun getCompletedChunks(fileId: String): List<FileChunk> {
        return getChunks(fileId).filter { it.isComplete() }
    }
    
    /**
     * Calculate file checksum
     */
    suspend fun calculateFileChecksum(fileId: String): String? = withContext(Dispatchers.IO) {
        return try {
            val metadata = metadataCache[fileId] ?: return@withContext null
            val chunks = getCompletedChunks(fileId).sortedBy { it.index }
            
            if (chunks.isEmpty()) return@withContext null
            
            val digest = MessageDigest.getInstance("SHA-256")
            
            chunks.forEach { chunk ->
                val chunkFile = File(chunksDir, "${fileId}_${chunk.index}")
                if (chunkFile.exists()) {
                    val bytes = chunkFile.readBytes()
                    digest.update(bytes)
                }
            }
            
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            Log.d(TAG, "Calculated checksum for $fileId: $checksum")
            checksum
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate checksum for $fileId", e)
            null
        }
    }
    
    /**
     * Save chunk data to disk
     */
    suspend fun saveChunkData(fileId: String, chunkIndex: Int, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        return try {
            val chunkFile = File(chunksDir, "${fileId}_${chunkIndex}")
            chunkFile.writeBytes(data)
            
            // Verify written data
            val writtenData = chunkFile.readBytes()
            val checksumMatch = data.contentEquals(writtenData)
            
            if (checksumMatch) {
                Log.d(TAG, "Saved chunk data for $fileId chunk $chunkIndex (${data.size} bytes)")
                true
            } else {
                Log.e(TAG, "Chunk data verification failed for $fileId chunk $chunkIndex")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chunk data for $fileId chunk $chunkIndex", e)
            false
        }
    }
    
    /**
     * Load chunk data from disk
     */
    suspend fun loadChunkData(fileId: String, chunkIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        return try {
            val chunkFile = File(chunksDir, "${fileId}_${chunkIndex}")
            if (chunkFile.exists()) {
                chunkFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chunk data for $fileId chunk $chunkIndex", e)
            null
        }
    }
    
    /**
     * Clean up chunks for a file
     */
    suspend fun cleanupChunks(fileId: String) = withContext(Dispatchers.IO) {
        try {
            // Remove chunk files
            chunksDir.listFiles { file ->
                file.name.startsWith("${fileId}_")
            }?.forEach { it.delete() }
            
            // Remove from cache
            chunkCache.remove(fileId)
            metadataCache.remove(fileId)
            
            // Update metadata file
            if (metadataCache.isEmpty()) {
                metadataFile.delete()
            } else {
                val remainingMetadata = metadataCache.values.first()
                saveChunkMetadata(remainingMetadata)
            }
            
            Log.d(TAG, "Cleaned up chunks for $fileId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup chunks for $fileId", e)
        }
    }
    
    /**
     * Get storage statistics
     */
    fun getStorageStats(): ChunkStorageStats {
        val totalFiles = chunksDir.listFiles()?.size ?: 0
        val totalSize = chunksDir.walkTopDown().filter { it.isFile() }.map { it.length() }.sum()
        
        return ChunkStorageStats(
            totalChunkFiles = totalFiles,
            totalSizeBytes = totalSize,
            activeFiles = chunkCache.size,
            metadataCount = metadataCache.size
        )
    }
}

/**
 * Storage statistics for chunks
 */
data class ChunkStorageStats(
    val totalChunkFiles: Int,
    val totalSizeBytes: Long,
    val activeFiles: Int,
    val metadataCount: Int
)
