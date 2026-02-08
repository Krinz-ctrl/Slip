package com.slip.app.ui.transfers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.data.repository.PersistentTransferRepository
import com.slip.app.data.repository.TransferStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for transfer history management
 */
class TransferHistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val persistentRepository = PersistentTransferRepository.getInstance(application)
    
    // State flows
    private val _activeTransfers = MutableStateFlow<List<TransferSession>>(emptyList())
    val activeTransfers: StateFlow<List<TransferSession>> = _activeTransfers.asStateFlow()
    
    private val _completedTransfers = MutableStateFlow<List<TransferSession>>(emptyList())
    val completedTransfers: StateFlow<List<TransferSession>> = _completedTransfers.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _transferStats = MutableStateFlow(TransferStats(0, 0, 0L, 0L, 0f))
    val transferStats: StateFlow<TransferStats> = _transferStats.asStateFlow()
    
    /**
     * Load all transfers
     */
    fun loadTransfers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val active = persistentRepository.getActiveTransfers()
                val completed = persistentRepository.getCompletedTransfers()
                val stats = persistentRepository.getTransferStats()
                
                _activeTransfers.value = active
                _completedTransfers.value = completed
                _transferStats.value = stats
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Pause a transfer
     */
    fun pauseTransfer(sessionId: String) {
        viewModelScope.launch {
            try {
                val transfer = persistentRepository.getTransferSession(sessionId)
                if (transfer != null && transfer.status == TransferStatus.IN_PROGRESS) {
                    // Update status to paused
                    persistentRepository.updateTransferStatus(sessionId, TransferStatus.PAUSED)
                    loadTransfers() // Refresh data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Resume a transfer
     */
    fun resumeTransfer(sessionId: String) {
        viewModelScope.launch {
            try {
                val transfer = persistentRepository.getTransferSession(sessionId)
                if (transfer != null && transfer.status == TransferStatus.PAUSED) {
                    // Update status to in progress
                    persistentRepository.updateTransferStatus(sessionId, TransferStatus.IN_PROGRESS)
                    loadTransfers() // Refresh data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Cancel a transfer
     */
    fun cancelTransfer(sessionId: String) {
        viewModelScope.launch {
            try {
                persistentRepository.cancelTransfer(sessionId)
                loadTransfers() // Refresh data
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Retry a failed transfer
     */
    fun retryTransfer(sessionId: String) {
        viewModelScope.launch {
            try {
                val success = persistentRepository.retryTransfer(sessionId)
                if (success) {
                    loadTransfers() // Refresh data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Clear completed transfers
     */
    fun clearCompletedTransfers() {
        viewModelScope.launch {
            try {
                // This would need to be implemented in the repository
                // For now, just reload data
                loadTransfers()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Clear failed transfers
     */
    fun clearFailedTransfers() {
        viewModelScope.launch {
            try {
                // This would need to be implemented in the repository
                // For now, just reload data
                loadTransfers()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Cleanup old transfers
     */
    fun cleanupOldTransfers() {
        viewModelScope.launch {
            try {
                persistentRepository.cleanupOldTransfers()
                loadTransfers() // Refresh data
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get transfer by ID
     */
    fun getTransfer(sessionId: String): TransferSession? {
        return persistentRepository.getTransferSession(sessionId)
    }
    
    /**
     * Refresh data
     */
    fun refresh() {
        loadTransfers()
    }
}
