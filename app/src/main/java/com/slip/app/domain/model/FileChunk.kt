package com.slip.app.domain.model

import java.util.UUID

/**
 * Represents a chunk of a file being transferred
 */
data class FileChunk(
    val id: String = UUID.randomUUID().toString(),
    val fileId: String,
    val index: Int,
    val totalChunks: Int,
    val startPosition: Long,
    val endPosition: Long,
    val size: Long,
    val checksum: String? = null,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val transferProgress: Float = 0f,
    val transferredBytes: Long = 0L,
    val retryCount: Int = 0,
    val lastAttemptTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    /**
     * Check if chunk is complete
     */
    fun isComplete(): Boolean = status == ChunkStatus.COMPLETED
    
    /**
     * Check if chunk has failed
     */
    fun hasFailed(): Boolean = status == ChunkStatus.FAILED
    
    /**
     * Check if chunk can be retried
     */
    fun canRetry(maxRetries: Int = 3): Boolean = retryCount < maxRetries
    
    /**
     * Get progress percentage
     */
    fun getProgressPercentage(): Int {
        return if (size > 0) {
            ((transferredBytes.toFloat() / size) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * Create a copy with updated status
     */
    fun withStatus(newStatus: ChunkStatus): FileChunk {
        return copy(
            status = newStatus,
            transferProgress = if (newStatus == ChunkStatus.COMPLETED) 1f else transferProgress,
            transferredBytes = if (newStatus == ChunkStatus.COMPLETED) size else transferredBytes,
            lastAttemptTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with updated progress
     */
    fun withProgress(bytesTransferred: Long): FileChunk {
        val progress = (bytesTransferred.toFloat() / size).coerceAtMost(1f)
        return copy(
            transferProgress = progress,
            transferredBytes = bytesTransferred
        )
    }
    
    /**
     * Create a copy with error
     */
    fun withError(error: String): FileChunk {
        return copy(
            status = ChunkStatus.FAILED,
            errorMessage = error,
            retryCount = retryCount + 1,
            lastAttemptTime = System.currentTimeMillis()
        )
    }
}

/**
 * Status of a chunk transfer
 */
enum class ChunkStatus {
    PENDING,        // Waiting to be transferred
    IN_PROGRESS,    // Currently being transferred
    COMPLETED,      // Successfully transferred
    FAILED,         // Transfer failed
    SKIPPED         // Chunk was skipped
}

/**
 * Chunk metadata for file transfer
 */
data class ChunkMetadata(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val fileChecksum: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedChunks: Set<Int> = emptySet(),
    val failedChunks: Set<Int> = emptySet(),
    val totalTransferredBytes: Long = 0L
) {
    /**
     * Calculate overall progress
     */
    fun getProgressPercentage(): Float {
        return if (totalChunks > 0) {
            (completedChunks.size.toFloat() / totalChunks) * 100f
        } else {
            0f
        }
    }
    
    /**
     * Get remaining chunks
     */
    fun getRemainingChunks(): List<Int> {
        return (0 until totalChunks).filter { it !in completedChunks }
    }
    
    /**
     * Get failed chunks that can be retried
     */
    fun getRetryableChunks(): List<Int> {
        return failedChunks.toList()
    }
    
    /**
     * Check if transfer is complete
     */
    fun isComplete(): Boolean {
        return completedChunks.size == totalChunks
    }
    
    /**
     * Get bytes transferred
     */
    fun getTransferredBytes(chunkSize: Int): Long {
        val completedBytes = completedChunks.size * chunkSize.toLong()
        // Last chunk might be smaller
        val lastChunkIndex = totalChunks - 1
        val lastChunkSize = if (lastChunkIndex in completedChunks) {
            fileSize - (lastChunkIndex * chunkSize.toLong())
        } else {
            0L
        }
        return completedBytes + lastChunkSize
    }
}

/**
 * Chunk manager for handling file chunking
 */
object ChunkManager {
    private const val DEFAULT_CHUNK_SIZE = 1024 * 1024 // 1MB chunks
    
    /**
     * Create chunks for a file
     */
    fun createChunks(fileId: String, fileSize: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<FileChunk> {
        val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
        val chunks = mutableListOf<FileChunk>()
        
        for (i in 0 until totalChunks) {
            val startPosition = (i * chunkSize.toLong())
            val endPosition = minOf(startPosition + chunkSize, fileSize)
            val size = endPosition - startPosition
            
            chunks.add(
                FileChunk(
                    fileId = fileId,
                    index = i,
                    totalChunks = totalChunks,
                    startPosition = startPosition,
                    endPosition = endPosition,
                    size = size
                )
            )
        }
        
        return chunks
    }
    
    /**
     * Create chunk metadata
     */
    fun createMetadata(fileId: String, fileName: String, fileSize: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): ChunkMetadata {
        val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
        return ChunkMetadata(
            fileId = fileId,
            fileName = fileName,
            fileSize = fileSize,
            chunkSize = chunkSize,
            totalChunks = totalChunks
        )
    }
    
    /**
     * Calculate optimal chunk size based on file size
     */
    fun calculateOptimalChunkSize(fileSize: Long): Int {
        return when {
            fileSize < 10 * 1024 * 1024 -> 256 * 1024      // < 10MB: 256KB chunks
            fileSize < 100 * 1024 * 1024 -> 1024 * 1024     // < 100MB: 1MB chunks
            fileSize < 1024 * 1024 * 1024 -> 2 * 1024 * 1024 // < 1GB: 2MB chunks
            else -> 4 * 1024 * 1024                           // >= 1GB: 4MB chunks
        }
    }
}
