package com.slip.app.data.repository

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.ChunkMetadata
import com.slip.app.domain.model.FileChunk
import com.slip.app.domain.model.ChunkStatus
import com.slip.app.service.work.TransferWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced repository for persistent transfer state management
 */
class PersistentTransferRepository private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PersistentTransferRepository"
        private const val TRANSFER_STATE_DIR = "transfer_state"
        private const val ACTIVE_TRANSFERS_FILE = "active_transfers.json"
        private const val COMPLETED_TRANSFERS_FILE = "completed_transfers.json"
        
        @Volatile
        private var INSTANCE: PersistentTransferRepository? = null
        
        fun getInstance(context: Context): PersistentTransferRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PersistentTransferRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val transferStateDir = File(context.filesDir, TRANSFER_STATE_DIR)
    private val activeTransfersFile = File(transferStateDir, ACTIVE_TRANSFERS_FILE)
    private val completedTransfersFile = File(transferStateDir, COMPLETED_TRANSFERS_FILE)
    private val chunkRepository = ChunkRepository.getInstance(context)
    private val transferWorkManager = TransferWorkManager(context)
    
    // In-memory cache
    private val activeTransfers = ConcurrentHashMap<String, TransferSession>()
    private val completedTransfers = ConcurrentHashMap<String, TransferSession>()
    
    init {
        transferStateDir.mkdirs()
        loadPersistedTransfers()
    }
    
    /**
     * Save transfer session with full state persistence
     */
    suspend fun saveTransferSession(session: TransferSession) = withContext(Dispatchers.IO) {
        try {
            activeTransfers[session.id] = session
            saveActiveTransfers()
            
            Log.d(TAG, "Saved transfer session: ${session.id} (${session.status})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transfer session", e)
        }
    }
    
    /**
     * Update transfer session status
     */
    suspend fun updateTransferStatus(sessionId: String, status: TransferStatus, error: String? = null) = withContext(Dispatchers.IO) {
        try {
            val session = activeTransfers[sessionId]
            if (session != null) {
                val updatedSession = session.copy(
                    status = status,
                    errorMessage = error,
                    endTime = if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.CANCELLED) {
                        System.currentTimeMillis()
                    } else {
                        session.endTime
                    }
                )
                
                activeTransfers[sessionId] = updatedSession
                saveActiveTransfers()
                
                // Move to completed if finished
                if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.CANCELLED) {
                    completedTransfers[sessionId] = updatedSession
                    activeTransfers.remove(sessionId)
                    saveCompletedTransfers()
                }
                
                Log.d(TAG, "Updated transfer status: $sessionId -> $status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update transfer status", e)
        }
    }
    
    /**
     * Resume interrupted transfers
     */
    suspend fun resumeInterruptedTransfers(): List<TransferSession> = withContext(Dispatchers.IO) {
        val resumableTransfers = mutableListOf<TransferSession>()
        
        try {
            // Check active transfers that were interrupted
            activeTransfers.values.forEach { session ->
                if (session.status == TransferStatus.IN_PROGRESS || session.status == TransferStatus.CONNECTING) {
                    // Check if transfer can be resumed
                    val canResume = canResumeTransfer(session)
                    if (canResume) {
                        resumableTransfers.add(session)
                        Log.d(TAG, "Found resumable transfer: ${session.id}")
                    } else {
                        // Mark as failed if cannot resume
                        updateTransferStatus(session.id, TransferStatus.FAILED, "Transfer cannot be resumed")
                    }
                }
            }
            
            // Restart WorkManager jobs for resumable transfers
            resumableTransfers.forEach { session ->
                transferWorkManager.restartTransfer(session)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume interrupted transfers", e)
        }
        
        resumableTransfers
    }
    
    /**
     * Check if transfer can be resumed
     */
    private suspend fun canResumeTransfer(session: TransferSession): Boolean {
        return try {
            // Check if chunk metadata exists
            val chunkMetadata = session.files.firstNotNullOfOrNull { file ->
                chunkRepository.getChunkMetadata(file.id)
            }
            
            if (chunkMetadata == null) {
                Log.w(TAG, "No chunk metadata found for transfer ${session.id}")
                return false
            }
            
            // Check if any chunks were completed
            val completedChunks = chunkRepository.getCompletedChunks(chunkMetadata.fileId)
            if (completedChunks.isEmpty()) {
                Log.w(TAG, "No completed chunks found for transfer ${session.id}")
                return false
            }
            
            // Check if source files still exist (for sending)
            if (session.type.name.startsWith("SEND")) {
                session.files.forEach { file ->
                    val contentResolver = context.contentResolver
                    val inputStream = contentResolver.openInputStream(file.uri)
                    inputStream?.close() ?: return false
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking resume capability for ${session.id}", e)
            false
        }
    }
    
    /**
     * Get transfer session by ID
     */
    fun getTransferSession(sessionId: String): TransferSession? {
        return activeTransfers[sessionId] ?: completedTransfers[sessionId]
    }
    
    /**
     * Get all active transfers
     */
    fun getActiveTransfers(): List<TransferSession> {
        return activeTransfers.values.toList()
    }
    
    /**
     * Get completed transfers
     */
    fun getCompletedTransfers(): List<TransferSession> {
        return completedTransfers.values.toList()
    }
    
    /**
     * Cancel transfer
     */
    suspend fun cancelTransfer(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val session = activeTransfers[sessionId]
            if (session != null) {
                // Cancel WorkManager job
                transferWorkManager.cancelTransfer(sessionId)
                
                // Update status
                updateTransferStatus(sessionId, TransferStatus.CANCELLED)
                
                // Clean up chunks
                session.files.forEach { file ->
                    chunkRepository.cleanupChunks(file.id)
                }
                
                Log.d(TAG, "Cancelled transfer: $sessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel transfer", e)
        }
    }
    
    /**
     * Retry failed transfer
     */
    suspend fun retryTransfer(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val session = completedTransfers[sessionId]
            if (session != null && session.status == TransferStatus.FAILED) {
                // Reset failed chunks
                session.files.forEach { file ->
                    val failedChunks = chunkRepository.getRetryableChunks(file.id)
                    failedChunks.forEach { chunk ->
                        chunkRepository.updateChunkStatus(file.id, chunk.index, ChunkStatus.PENDING)
                    }
                }
                
                // Move back to active and restart
                val resetSession = session.copy(
                    status = TransferStatus.PENDING,
                    errorMessage = null,
                    endTime = null
                )
                
                activeTransfers[sessionId] = resetSession
                completedTransfers.remove(sessionId)
                
                saveActiveTransfers()
                saveCompletedTransfers()
                
                // Restart transfer
                transferWorkManager.restartTransfer(resetSession)
                
                Log.d(TAG, "Retrying transfer: $sessionId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retry transfer", e)
            false
        }
    }
    
    /**
     * Clean up old completed transfers
     */
    suspend fun cleanupOldTransfers(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) = withContext(Dispatchers.IO) { // 7 days
        try {
            val now = System.currentTimeMillis()
            val toRemove = mutableListOf<String>()
            
            completedTransfers.forEach { (id, session) ->
                val age = now - (session.endTime ?: session.startTime)
                if (age > maxAgeMs) {
                    toRemove.add(id)
                    
                    // Clean up chunks
                    session.files.forEach { file ->
                        chunkRepository.cleanupChunks(file.id)
                    }
                }
            }
            
            toRemove.forEach { id ->
                completedTransfers.remove(id)
            }
            
            if (toRemove.isNotEmpty()) {
                saveCompletedTransfers()
                Log.d(TAG, "Cleaned up ${toRemove.size} old transfers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old transfers", e)
        }
    }
    
    /**
     * Get transfer statistics
     */
    fun getTransferStats(): TransferStats {
        val active = activeTransfers.values
        val completed = completedTransfers.values
        
        return TransferStats(
            activeTransfers = active.count(),
            completedTransfers = completed.count(),
            totalBytesTransferred = completed.sumOf { it.transferredSize },
            totalFileSize = active.sumOf { it.totalSize } + completed.sumOf { it.totalSize },
            successRate = if (completed.isNotEmpty()) {
                completed.count { it.status == TransferStatus.COMPLETED }.toFloat() / completed.size
            } else {
                0f
            }
        )
    }
    
    /**
     * Load persisted transfers from disk
     */
    private fun loadPersistedTransfers() {
        try {
            // Load active transfers
            if (activeTransfersFile.exists()) {
                val json = activeTransfersFile.readText()
                val transfers = com.google.gson.Gson().fromJson(json, Array<TransferSession>::class.java)
                transfers?.forEach { session ->
                    activeTransfers[session.id] = session
                }
                Log.d(TAG, "Loaded ${activeTransfers.size} active transfers")
            }
            
            // Load completed transfers
            if (completedTransfersFile.exists()) {
                val json = completedTransfersFile.readText()
                val transfers = com.google.gson.Gson().fromJson(json, Array<TransferSession>::class.java)
                transfers?.forEach { session ->
                    completedTransfers[session.id] = session
                }
                Log.d(TAG, "Loaded ${completedTransfers.size} completed transfers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted transfers", e)
        }
    }
    
    /**
     * Save active transfers to disk
     */
    private fun saveActiveTransfers() {
        try {
            val json = com.google.gson.Gson().toJson(activeTransfers.values.toList())
            activeTransfersFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save active transfers", e)
        }
    }
    
    /**
     * Save completed transfers to disk
     */
    private fun saveCompletedTransfers() {
        try {
            val json = com.google.gson.Gson().toJson(completedTransfers.values.toList())
            completedTransfersFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save completed transfers", e)
        }
    }
}

/**
 * Transfer statistics
 */
data class TransferStats(
    val activeTransfers: Int,
    val completedTransfers: Int,
    val totalBytesTransferred: Long,
    val totalFileSize: Long,
    val successRate: Float
)
