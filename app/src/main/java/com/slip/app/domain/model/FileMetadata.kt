package com.slip.app.domain.model

import android.net.Uri
import android.provider.DocumentsContract
import android.content.ContentResolver
import android.util.Log

/**
 * Represents metadata for a file being transferred
 */
data class FileMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri,
    val lastModified: Long = 0L,
    val checksum: String? = null,
    val relativePath: String = "",
    val isDirectory: Boolean = false,
    val parentId: String? = null,
    val transferStatus: FileTransferStatus = FileTransferStatus.PENDING,
    val transferredBytes: Long = 0L,
    val errorMessage: String? = null
) {
    /**
     * Calculate file transfer progress
     */
    fun getProgressPercentage(): Int {
        return if (size > 0) {
            ((transferredBytes.toFloat() / size) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * Check if file transfer is complete
     */
    fun isComplete(): Boolean {
        return transferStatus == FileTransferStatus.COMPLETED
    }
    
    /**
     * Check if file transfer is active
     */
    fun isActive(): Boolean {
        return transferStatus == FileTransferStatus.IN_PROGRESS
    }
    
    /**
     * Get human-readable file size
     */
    fun getFormattedSize(): String {
        return formatFileSize(size)
    }
    
    /**
     * Get human-readable transferred size
     */
    fun getFormattedTransferredSize(): String {
        return formatFileSize(transferredBytes)
    }
    
    companion object {
        private const val TAG = "FileMetadata"
        
        /**
         * Create FileMetadata from URI
         */
        fun fromUri(contentResolver: ContentResolver, uri: Uri, relativePath: String = ""): FileMetadata? {
            return try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                        val size = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                        val mimeType = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        val lastModified = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                        
                        FileMetadata(
                            id = uri.toString(),
                            name = name,
                            size = size,
                            mimeType = mimeType ?: "application/octet-stream",
                            uri = uri,
                            lastModified = lastModified,
                            relativePath = relativePath,
                            isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == mimeType
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create FileMetadata from URI: $uri", e)
                null
            }
        }
        
        /**
         * Format file size in human-readable format
         */
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.1f GB".format(gb)
        }
    }
}

/**
 * Status of individual file transfer
 */
enum class FileTransferStatus {
    PENDING,        // Waiting to transfer
    IN_PROGRESS,    // Currently transferring
    COMPLETED,      // Successfully transferred
    FAILED,         // Transfer failed
    SKIPPED         // File was skipped
}
