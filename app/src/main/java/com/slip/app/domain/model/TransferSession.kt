package com.slip.app.domain.model

import java.util.UUID

/**
 * Represents a file transfer session between devices
 */
data class TransferSession(
    val id: String = UUID.randomUUID().toString(),
    val type: TransferType,
    val status: TransferStatus = TransferStatus.PENDING,
    val files: List<FileMetadata> = emptyList(),
    val rootFolder: FolderNode? = null,
    val totalSize: Long = 0L,
    val transferredSize: Long = 0L,
    val progress: Float = 0f,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val senderDeviceId: String? = null,
    val receiverDeviceId: String? = null,
    val errorMessage: String? = null,
    val isPaused: Boolean = false
) {
    /**
     * Calculate transfer progress percentage
     */
    fun getProgressPercentage(): Int {
        return if (totalSize > 0) {
            ((transferredSize.toFloat() / totalSize) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * Get transfer speed in bytes per second
     */
    fun getTransferSpeed(): Long {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - startTime) / 1000 // Convert to seconds
        return if (elapsedTime > 0) {
            transferredSize / elapsedTime
        } else {
            0
        }
    }
    
    /**
     * Get estimated remaining time in seconds
     */
    fun getEstimatedTimeRemaining(): Long {
        val speed = getTransferSpeed()
        val remainingSize = totalSize - transferredSize
        return if (speed > 0) {
            remainingSize / speed
        } else {
            -1 // Unknown
        }
    }
    
    /**
     * Check if transfer is complete
     */
    fun isComplete(): Boolean {
        return status == TransferStatus.COMPLETED || status == TransferStatus.FAILED
    }
    
    /**
     * Check if transfer is active
     */
    fun isActive(): Boolean {
        return status == TransferStatus.IN_PROGRESS && !isPaused
    }
}

/**
 * Types of transfer operations
 */
enum class TransferType {
    SEND_FILES,
    SEND_FOLDER,
    RECEIVE_FILES,
    RECEIVE_FOLDER
}

/**
 * Status of a transfer session
 */
enum class TransferStatus {
    PENDING,        // Waiting to start
    CONNECTING,     // Establishing connection
    IN_PROGRESS,    // Currently transferring
    PAUSED,         // Temporarily paused
    COMPLETED,      // Successfully completed
    FAILED,         // Transfer failed
    CANCELLED       // User cancelled
}
