package com.slip.app.data.repository

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.service.work.TransferWorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Repository for managing transfer sessions and persistence
 */
class TransferRepository private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TransferRepository"
        
        @Volatile
        private var INSTANCE: TransferRepository? = null
        
        fun getInstance(context: Context): TransferRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val workManager = TransferWorkManager(context)
    
    // In-memory transfer session cache (in a real app, this would be persisted to database)
    private val _currentTransferSession = MutableStateFlow<TransferSession?>(null)
    val currentTransferSession: Flow<TransferSession?> = _currentTransferSession.asStateFlow()
    
    // Transfer history
    private val _transferHistory = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferHistory: Flow<List<TransferSession>> = _transferHistory.asStateFlow()
    
    /**
     * Start a new transfer
     */
    fun startTransfer(transferSession: TransferSession) {
        Log.d(TAG, "Starting transfer: ${transferSession.id}")
        
        // Update current session
        _currentTransferSession.value = transferSession.copy(status = TransferStatus.CONNECTING)
        
        // Schedule work
        workManager.scheduleTransfer(transferSession)
        
        // Add to history
        addToHistory(transferSession)
    }
    
    /**
     * Pause current transfer
     */
    fun pauseTransfer() {
        Log.d(TAG, "Pausing transfer")
        workManager.cancelTransfer()
        
        _currentTransferSession.value?.let { session ->
            _currentTransferSession.value = session.copy(
                status = TransferStatus.PAUSED,
                isPaused = true
            )
        }
    }
    
    /**
     * Resume transfer
     */
    fun resumeTransfer() {
        Log.d(TAG, "Resuming transfer")
        
        _currentTransferSession.value?.let { session ->
            if (session.status == TransferStatus.PAUSED) {
                val resumedSession = session.copy(
                    status = TransferStatus.CONNECTING,
                    isPaused = false
                )
                _currentTransferSession.value = resumedSession
                
                // Reschedule work
                workManager.scheduleTransfer(resumedSession)
            }
        }
    }
    
    /**
     * Cancel transfer
     */
    fun cancelTransfer() {
        Log.d(TAG, "Cancelling transfer")
        workManager.cancelTransfer()
        
        _currentTransferSession.value?.let { session ->
            _currentTransferSession.value = session.copy(
                status = TransferStatus.CANCELLED,
                endTime = System.currentTimeMillis()
            )
            
            // Update history
            updateInHistory(session.copy(
                status = TransferStatus.CANCELLED,
                endTime = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Get transfer progress
     */
    fun getTransferProgress(): Flow<Int> {
        return workManager.getTransferProgress()
    }
    
    /**
     * Get transfer status
     */
    fun getTransferStatus(): Flow<TransferStatus> {
        return workManager.getTransferStatus()
    }
    
    /**
     * Check if transfer is active
     */
    fun isTransferActive(): Boolean {
        return workManager.isTransferRunning()
    }
    
    /**
     * Get transfer statistics
     */
    fun getTransferStatistics() = workManager.getWorkStatistics()
    
    /**
     * Handle app restart - restore transfer state
     */
    fun restoreTransferState() {
        Log.d(TAG, "Restoring transfer state")
        
        if (workManager.isTransferRunning()) {
            // There's an active transfer, restore it
            // In a real implementation, we would load from database
            val restoredSession = createRestoredSession()
            _currentTransferSession.value = restoredSession
        }
    }
    
    /**
     * Clear completed transfers from history
     */
    fun clearCompletedTransfers() {
        val currentHistory = _transferHistory.value
        val activeTransfers = currentHistory.filter { 
            it.status != TransferStatus.COMPLETED && it.status != TransferStatus.FAILED
        }
        _transferHistory.value = activeTransfers
    }
    
    /**
     * Get all transfers (current + history)
     */
    fun getAllTransfers(): Flow<List<TransferSession>> {
        return combine(
            currentTransferSession,
            transferHistory
        ) { current, history ->
            val allTransfers = mutableListOf<TransferSession>()
            current?.let { allTransfers.add(it) }
            allTransfers.addAll(history)
            allTransfers.sortedByDescending { it.startTime }
        }
    }
    
    /**
     * Update transfer session (called by TransferService)
     */
    fun updateTransferSession(session: TransferSession) {
        Log.d(TAG, "Updating transfer session: ${session.id}")
        
        // Update current session if it matches
        _currentTransferSession.value?.let { current ->
            if (current.id == session.id) {
                _currentTransferSession.value = session
            }
        }
        
        // Update in history
        updateInHistory(session)
    }
    
    private fun addToHistory(session: TransferSession) {
        val currentHistory = _transferHistory.value.toMutableList()
        currentHistory.add(session)
        _transferHistory.value = currentHistory
    }
    
    private fun updateInHistory(session: TransferSession) {
        val currentHistory = _transferHistory.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            currentHistory[index] = session
            _transferHistory.value = currentHistory
        }
    }
    
    private fun createRestoredSession(): TransferSession {
        // In a real implementation, this would load from database
        // For now, create a dummy session
        return TransferSession(
            id = "restored_transfer",
            type = com.slip.app.domain.model.TransferType.SEND_FILES,
            status = TransferStatus.IN_PROGRESS,
            totalSize = 1000000L,
            transferredSize = 500000L,
            progress = 50f,
            startTime = System.currentTimeMillis() - 60000 // Started 1 minute ago
        )
    }
}
